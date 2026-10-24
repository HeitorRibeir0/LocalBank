package com.localbank.finance.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Gerencia o conceito de "lar" (household).
 * Usa EncryptedSharedPreferences para guardar o householdId com segurança.
 */
object HouseholdManager {

    private const val PREFS_NAME = "secure_household_prefs"
    private const val KEY_HOUSEHOLD_ID = "household_id"
    private const val KEY_NEEDS_DOWNLOAD = "needs_initial_download"
    private const val KEY_UPLOAD_DONE = "initial_upload_done"
    private const val MAX_MEMBERS = 5

    private val db = FirebaseFirestore.getInstance()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getSecurePrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }

        return synchronized(this) {
            cachedPrefs ?: run {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPrefs = it }
            }
        }
    }

    fun getHouseholdId(context: Context): String? {
        return getSecurePrefs(context).getString(KEY_HOUSEHOLD_ID, null)
    }

    private fun saveHouseholdId(context: Context, id: String) {
        getSecurePrefs(context).edit().putString(KEY_HOUSEHOLD_ID, id).apply()
    }

    fun clearHouseholdId(context: Context) {
        getSecurePrefs(context).edit()
            .remove(KEY_HOUSEHOLD_ID)
            .remove(KEY_NEEDS_DOWNLOAD)
            .remove(KEY_UPLOAD_DONE)
            .apply()
    }

    /**
     * Retorna true se o usuário acabou de entrar num lar existente
     * e precisa baixar os dados do Firestore antes de qualquer upload.
     */
    fun needsInitialDownload(context: Context): Boolean {
        return getSecurePrefs(context).getBoolean(KEY_NEEDS_DOWNLOAD, false)
    }

    fun clearNeedsInitialDownload(context: Context) {
        getSecurePrefs(context).edit().putBoolean(KEY_NEEDS_DOWNLOAD, false).apply()
    }

    /**
     * Dados locais já foram enviados ao lar. Depois disso, cada alteração é enviada
     * individualmente pelo repositório — não é preciso reenviar tudo a cada abertura.
     */
    fun isInitialUploadDone(context: Context): Boolean =
        getSecurePrefs(context).getBoolean(KEY_UPLOAD_DONE, false)

    fun markInitialUploadDone(context: Context) {
        getSecurePrefs(context).edit().putBoolean(KEY_UPLOAD_DONE, true).apply()
    }

    /**
     * Busca no Firestore se o usuário já pertence a algum lar.
     * Útil após reinstalar o app (dados locais são perdidos).
     * Retorna o householdId se encontrar, null se não.
     */
    suspend fun recoverHousehold(context: Context, userId: String): String? {
        // Primeiro verifica se já tem salvo localmente
        val local = getHouseholdId(context)
        if (local != null) return local

        // Busca no Firestore
        val result = db.collection("households")
            .whereArrayContains("members", userId)
            .get()
            .await()

        val doc = result.documents.firstOrNull() ?: return null
        val householdId = doc.id
        saveHouseholdId(context, householdId)
        // Reinstalação: o banco local só tem os dados padrão. Baixar em vez de
        // enviar evita duplicar categorias/contas padrão no lar.
        getSecurePrefs(context).edit().putBoolean(KEY_NEEDS_DOWNLOAD, true).apply()
        return householdId
    }

    /**
     * Cria um novo lar. Convite válido por 1 hora (mais seguro que 24h).
     */
    suspend fun createHousehold(
        context: Context,
        userId: String,
        displayName: String
    ): String {
        val inviteCode = generateInviteCode()
        val householdRef = db.collection("households").document()
        val householdId = householdRef.id

        val data = hashMapOf(
            "ownerId" to userId,
            "members" to listOf(userId),
            "memberNames" to hashMapOf(userId to displayName),
            "inviteCode" to inviteCode,
            "inviteExpiresAt" to (System.currentTimeMillis() + 1 * 60 * 60 * 1000), // 1 hora
            "createdAt" to System.currentTimeMillis()
        )

        householdRef.set(data).await()

        db.collection("invites").document(inviteCode).set(
            hashMapOf("householdId" to householdId)
        ).await()

        saveHouseholdId(context, householdId)
        return inviteCode
    }

    /**
     * Gera um novo código de convite para o lar ATUAL (sem criar outro lar).
     * O código anterior, se existir, é invalidado.
     */
    suspend fun createInvite(householdId: String): String {
        val householdRef = db.collection("households").document(householdId)
        val oldCode = householdRef.get().await().getString("inviteCode")

        val inviteCode = generateInviteCode()
        householdRef.update(
            mapOf(
                "inviteCode" to inviteCode,
                "inviteExpiresAt" to (System.currentTimeMillis() + 1 * 60 * 60 * 1000) // 1 hora
            )
        ).await()

        db.collection("invites").document(inviteCode).set(
            hashMapOf("householdId" to householdId)
        ).await()

        if (oldCode != null && oldCode != inviteCode) {
            db.collection("invites").document(oldCode).delete().await()
        }
        return inviteCode
    }

    /**
     * Entra num lar existente. Após uso, o convite é invalidado.
     */
    suspend fun joinHousehold(
        context: Context,
        userId: String,
        displayName: String,
        inviteCode: String
    ): Boolean {
        val cleanCode = inviteCode.uppercase().trim()

        val inviteDoc = db.collection("invites")
            .document(cleanCode)
            .get()
            .await()

        if (!inviteDoc.exists()) return false

        val householdId = inviteDoc.getString("householdId") ?: return false

        val householdDoc = db.collection("households")
            .document(householdId)
            .get()
            .await()

        if (!householdDoc.exists()) return false

        // Verificar expiração
        val expiresAt = householdDoc.getLong("inviteExpiresAt") ?: 0
        if (System.currentTimeMillis() > expiresAt) return false

        // Entrada numa transação: duas pessoas entrando ao mesmo tempo não passam do limite.
        // Resultado: 0 = recusado, 1 = entrou agora, 2 = já era membro.
        val householdRef = db.collection("households").document(householdId)
        val result = db.runTransaction<Int> { tx ->
            val snap = tx.get(householdRef)
            if (!snap.exists()) return@runTransaction 0
            val members = ((snap.get("members") as? List<*>) ?: emptyList<Any?>()).map { it.toString() }
            if (members.contains(userId)) return@runTransaction 2
            if (members.size >= MAX_MEMBERS) return@runTransaction 0
            val names = HashMap<String, Any?>()
            (snap.get("memberNames") as? Map<*, *>)?.forEach { (k, v) -> names[k.toString()] = v }
            names[userId] = displayName
            tx.update(householdRef, mapOf("members" to members + userId, "memberNames" to names))
            1
        }.await()
        if (result == 0) return false
        if (result == 2) {
            saveHouseholdId(context, householdId)
            return true
        }

        // Invalidar convite (uso único)
        db.collection("invites").document(cleanCode).delete().await()

        saveHouseholdId(context, householdId)

        // Marcar que este membro precisa baixar os dados existentes
        getSecurePrefs(context).edit().putBoolean(KEY_NEEDS_DOWNLOAD, true).apply()

        return true
    }

    suspend fun getMemberNames(householdId: String): Map<String, String> {
        val doc = db.collection("households").document(householdId).get().await()
        @Suppress("UNCHECKED_CAST")
        return (doc.get("memberNames") as? Map<String, String>) ?: emptyMap()
    }

    /**
     * Código de 6 caracteres, sem caracteres ambíguos (I, O, 0, 1).
     * 32^6 = ~1 bilhão de combinações possíveis.
     */
    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
