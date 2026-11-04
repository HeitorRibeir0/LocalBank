// Roda dentro de `firebase emulators:exec` (npm test): FIRESTORE_EMULATOR_HOST já vem definido.
process.env.GCLOUD_PROJECT ??= "demo-localbank";

const { test, before, beforeEach, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { getFirestore } = require("firebase-admin/firestore");
const fns = require("../index.js");

let env;
const auth = (uid, name) => ({ auth: { uid, token: { name } } });
const call = (fn, uid, data, name = uid) => fn.run({ ...auth(uid, name), data });

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT,
    firestore: { rules: fs.readFileSync(path.join(__dirname, "../../firestore.rules"), "utf8") },
  });
});
beforeEach(() => env.clearFirestore());
after(() => env.cleanup());

test("criar lar e entrar com o código", async () => {
  const { householdId, inviteCode } = await call(fns.createHousehold, "ana", {}, "Ana");
  assert.match(inviteCode, /^[A-HJ-NP-Z2-9]{6}$/);

  const res = await call(fns.joinHousehold, "bia", { code: inviteCode.toLowerCase() }, "Bia");
  assert.deepEqual(res, { joined: true, householdId, alreadyMember: false });

  const house = (await getFirestore().doc(`households/${householdId}`).get()).data();
  assert.deepEqual(house.members, ["ana", "bia"]);
  assert.equal(house.memberNames.bia, "Bia");
  assert.equal(house.inviteCode, undefined);
  assert.equal((await getFirestore().doc(`invites/${inviteCode}`).get()).exists, false);
});

test("convite é de uso único", async () => {
  const { inviteCode } = await call(fns.createHousehold, "ana", {});
  await call(fns.joinHousehold, "bia", { code: inviteCode });
  assert.deepEqual(await call(fns.joinHousehold, "caio", { code: inviteCode }), { joined: false });
});

test("convite vencido é recusado e apagado", async () => {
  const { inviteCode } = await call(fns.createHousehold, "ana", {});
  await getFirestore().doc(`invites/${inviteCode}`).update({ expiresAt: Date.now() - 1 });
  assert.deepEqual(await call(fns.joinHousehold, "bia", { code: inviteCode }), { joined: false });
  assert.equal((await getFirestore().doc(`invites/${inviteCode}`).get()).exists, false);
});

test("código malformado é recusado sem consultar", async () => {
  assert.deepEqual(await call(fns.joinHousehold, "bia", { code: "../x" }), { joined: false });
  assert.deepEqual(await call(fns.joinHousehold, "bia", {}), { joined: false });
});

test("lar cheio recusa o sexto membro", async () => {
  const { householdId } = await call(fns.createHousehold, "m0", {});
  await getFirestore().doc(`households/${householdId}`).update({ members: ["m0", "m1", "m2", "m3", "m4"] });
  const { inviteCode } = await call(fns.createInvite, "m0", { householdId });
  assert.deepEqual(await call(fns.joinHousehold, "m5", { code: inviteCode }), { joined: false });
});

test("só membro gera convite novo, e o antigo deixa de valer", async () => {
  const { householdId, inviteCode: first } = await call(fns.createHousehold, "ana", {});
  await assert.rejects(call(fns.createInvite, "estranho", { householdId }), { code: "permission-denied" });

  const { inviteCode: second } = await call(fns.createInvite, "ana", { householdId });
  assert.notEqual(second, first);
  assert.equal((await getFirestore().doc(`invites/${first}`).get()).exists, false);
  assert.equal((await call(fns.joinHousehold, "bia", { code: second })).joined, true);
});

test("sem login as functions recusam", async () => {
  await assert.rejects(fns.createHousehold.run({ data: {} }), { code: "unauthenticated" });
});

// ── Regras ──

async function seedHousehold() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc("households/h1").set({ ownerId: "ana", members: ["ana"], inviteCode: "ABCDEF", inviteExpiresAt: Date.now() + 60000 });
    await db.doc("households/h1/transactions/t1").set({ amountCents: 100 });
    await db.doc("invites/ABCDEF").set({ householdId: "h1", expiresAt: Date.now() + 60000 });
  });
}

test("regras: membro lê o lar e os dados; não-membro não", async () => {
  await seedHousehold();
  const ana = env.authenticatedContext("ana").firestore();
  const intruso = env.authenticatedContext("intruso").firestore();

  await assertSucceeds(ana.doc("households/h1").get());
  await assertSucceeds(ana.collection("households").where("members", "array-contains", "ana").get());
  await assertSucceeds(ana.doc("households/h1/transactions/t1").get());
  await assertSucceeds(ana.doc("households/h1/transactions/t2").set({ amountCents: 5 }));

  await assertFails(intruso.doc("households/h1").get());
  await assertFails(intruso.doc("households/h1/transactions/t1").get());
  await assertFails(intruso.collection("households").where("members", "array-contains", "ana").get());
});

test("regras: ninguém se adiciona ao lar nem mexe em convites pelo app", async () => {
  await seedHousehold();
  const intruso = env.authenticatedContext("intruso").firestore();
  const ana = env.authenticatedContext("ana").firestore();

  await assertFails(intruso.doc("households/h1").update({ members: ["ana", "intruso"] }));
  await assertFails(ana.doc("households/h1").update({ members: ["ana", "amigo"] }));
  await assertFails(intruso.doc("households/novo").set({ ownerId: "intruso", members: ["intruso"] }));
  await assertFails(intruso.doc("invites/ABCDEF").get());
  await assertFails(intruso.doc("invites/ZZZZZZ").set({ householdId: "h1" }));
  await assertFails(ana.doc("invites/ABCDEF").delete());
});
