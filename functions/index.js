// Operações de lar que o app não pode fazer sozinho: as regras do Firestore fecham
// `invites` e a escrita no documento do lar, então tudo passa por aqui (Admin SDK).
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

initializeApp();
setGlobalOptions({ maxInstances: 5 });

const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem I, O, 0, 1
const CODE_LENGTH = 6;
const INVITE_TTL_MS = 60 * 60 * 1000;
const MAX_MEMBERS = 5;

const db = () => getFirestore();
const households = () => db().collection("households");
const invites = () => db().collection("invites");

function requireUid(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Entre na sua conta para continuar.");
  return request.auth.uid;
}

// O nome do token é o da conta Google; o enviado pelo app é só reserva
function memberName(request) {
  const fromToken = request.auth.token && request.auth.token.name;
  const fromApp = typeof request.data?.displayName === "string" ? request.data.displayName.trim() : "";
  return (fromToken || fromApp || "Membro").slice(0, 60);
}

function randomCode() {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_CHARS[crypto.randomInt(CODE_CHARS.length)];
  return code;
}

// Lê dentro da transação para não sobrescrever um convite ativo de outro lar
async function freeCode(tx) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = randomCode();
    const snap = await tx.get(invites().doc(code));
    if (!snap.exists) return code;
  }
  throw new HttpsError("unavailable", "Não foi possível gerar o código. Tente de novo.");
}

exports.createHousehold = onCall(async (request) => {
  const uid = requireUid(request);
  const name = memberName(request);
  const householdRef = households().doc();

  const inviteCode = await db().runTransaction(async (tx) => {
    const code = await freeCode(tx);
    const now = Date.now();
    tx.set(householdRef, {
      ownerId: uid,
      members: [uid],
      memberNames: { [uid]: name },
      inviteCode: code,
      inviteExpiresAt: now + INVITE_TTL_MS,
      createdAt: now,
    });
    tx.set(invites().doc(code), { householdId: householdRef.id, expiresAt: now + INVITE_TTL_MS });
    return code;
  });

  return { householdId: householdRef.id, inviteCode };
});

exports.createInvite = onCall(async (request) => {
  const uid = requireUid(request);
  const householdId = request.data?.householdId;
  if (typeof householdId !== "string" || householdId.length === 0 || householdId.includes("/")) {
    throw new HttpsError("invalid-argument", "Lar inválido.");
  }
  const householdRef = households().doc(householdId);

  const inviteCode = await db().runTransaction(async (tx) => {
    const snap = await tx.get(householdRef);
    if (!snap.exists || !(snap.get("members") || []).includes(uid)) {
      throw new HttpsError("permission-denied", "Você não faz parte deste lar.");
    }
    const code = await freeCode(tx);
    const expiresAt = Date.now() + INVITE_TTL_MS;
    const oldCode = snap.get("inviteCode");
    if (oldCode && oldCode !== code) tx.delete(invites().doc(oldCode));
    tx.set(invites().doc(code), { householdId, expiresAt });
    tx.update(householdRef, { inviteCode: code, inviteExpiresAt: expiresAt });
    return code;
  });

  return { inviteCode };
});

// Resposta de recusa não diz o motivo exato, para não ajudar quem tenta adivinhar códigos
exports.joinHousehold = onCall(async (request) => {
  const uid = requireUid(request);
  const name = memberName(request);
  const code = String(request.data?.code ?? "").toUpperCase().trim();
  const refused = { joined: false };
  if (code.length !== CODE_LENGTH || [...code].some((c) => !CODE_CHARS.includes(c))) return refused;

  const inviteRef = invites().doc(code);
  return db().runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists) return refused;
    const householdRef = households().doc(invite.get("householdId"));
    const household = await tx.get(householdRef);

    if (!household.exists || invite.get("expiresAt") < Date.now()) {
      tx.delete(inviteRef);
      return refused;
    }
    const members = household.get("members") || [];
    if (members.includes(uid)) return { joined: true, householdId: householdRef.id, alreadyMember: true };
    if (members.length >= MAX_MEMBERS) return refused;

    // Convite de uso único
    tx.delete(inviteRef);
    tx.update(householdRef, {
      members: FieldValue.arrayUnion(uid),
      [`memberNames.${uid}`]: name,
      inviteCode: FieldValue.delete(),
      inviteExpiresAt: 0,
    });
    return { joined: true, householdId: householdRef.id, alreadyMember: false };
  });
});
