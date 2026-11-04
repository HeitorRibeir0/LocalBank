package com.localbank.finance.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Gerencia o conceito de "lar" (household).
 * Usa EncryptedSharedPreferences para guardar o householdId com segurança.
 */
object HouseholdManager {

    private const val PREFS_NAME = "secure_household_prefs"
    private const val KEY_HOUSEHOLD_ID = "household_id"
    private const val KEY_NEEDS_DOWNLOAD = "needs_initial_download"
    private const val KEY_UPLOAD_DONE = "initial_upload_done"

    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getSecurePrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }

        return synchronized(this) {
            cachedPrefs ?: run {
                try {
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
                } catch (e: Exception) {
                    // Falha ao ler preferências criptografadas (ex.: troca de celular).
                    // Limpar e recriar para que o app se recupere.
                    try {
                        context.deleteSharedPreferences(PREFS_NAME)
                    } catch (_: Exception) {}

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

    /** Cria um novo lar (Cloud Function `createHousehold`) e devolve o código de convite, válido por 1 hora. */
    suspend fun createHousehold(context: Context, displayName: String): String {
        val data = call("createHousehold", mapOf("displayName" to displayName))
        saveHouseholdId(context, data["householdId"] as String)
        return data["inviteCode"] as String
    }

    /** Gera um código novo para o lar atual; o anterior deixa de valer. */
    suspend fun createInvite(householdId: String): String =
        call("createInvite", mapOf("householdId" to householdId))["inviteCode"] as String

    /** Entra num lar pelo código. Falso se o código não existir, tiver vencido ou o lar estiver cheio. */
    suspend fun joinHousehold(context: Context, displayName: String, inviteCode: String): Boolean {
        val data = call("joinHousehold", mapOf("code" to inviteCode.uppercase().trim(), "displayName" to displayName))
        if (data["joined"] != true) return false
        saveHouseholdId(context, data["householdId"] as String)
        // Membro novo baixa os dados do lar antes de enviar os seus
        if (data["alreadyMember"] != true) {
            getSecurePrefs(context).edit().putBoolean(KEY_NEEDS_DOWNLOAD, true).apply()
        }
        return true
    }

    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> =
        functions.getHttpsCallable(name).call(data).await().getData() as Map<*, *>

    suspend fun getMemberNames(householdId: String): Map<String, String> {
        val doc = db.collection("households").document(householdId).get().await()
        @Suppress("UNCHECKED_CAST")
        return (doc.get("memberNames") as? Map<String, String>) ?: emptyMap()
    }
}
