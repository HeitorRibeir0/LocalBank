package com.localbank.finance.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Senha do SQLCipher, cifrada com uma chave AES do Android Keystore e guardada em
 * SharedPreferences comum (só o texto cifrado sai do Keystore).
 */
internal object DbPassphrase {

    private const val TAG = "DbPassphrase"
    private const val KEY_ALIAS = "localbank_db_key"
    private const val PREFS = "db_key_v2"
    private const val PREF_CIPHERTEXT = "ciphertext"
    private const val PREF_IV = "iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    // Armazenamento antigo (security-crypto, descontinuada): lido uma vez para migrar
    private const val LEGACY_PREFS = "secure_db_prefs"
    private const val LEGACY_KEY = "db_passphrase"

    /**
     * Devolve a senha e se ela é nova. Senha nova com banco já existente quer dizer que a antiga
     * se perdeu (chave do Keystore apagada, restauração em outro aparelho): o banco não abre mais
     * e precisa ser recriado — os dados do lar voltam pelo Firestore.
     */
    fun getOrCreate(context: Context): Pair<ByteArray, Boolean> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(PREF_CIPHERTEXT, null)
        val iv = prefs.getString(PREF_IV, null)
        if (ciphertext != null && iv != null) {
            try {
                return decrypt(Base64.decode(ciphertext, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP)) to false
            } catch (e: Exception) {
                Log.e(TAG, "Senha do banco ilegível", e)
                reset(context)
            }
        }

        val legacy = readLegacy(context)
        val passphrase = legacy ?: newPassphrase()
        store(context, passphrase)
        context.deleteSharedPreferences(LEGACY_PREFS)
        return passphrase to (legacy == null)
    }

    private fun reset(context: Context) {
        context.deleteSharedPreferences(PREFS)
        context.deleteSharedPreferences(LEGACY_PREFS)
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao apagar a chave", e)
        }
    }

    private fun readLegacy(context: Context): ByteArray? = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, LEGACY_PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).getString(LEGACY_KEY, null)?.toByteArray()
    } catch (e: Exception) {
        Log.w(TAG, "Senha antiga ilegível", e)
        null
    }

    // Texto hexadecimal, como a senha antiga (UUIDs), para o SQLCipher receber sempre bytes imprimíveis
    private fun newPassphrase(): ByteArray {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }.toByteArray()
    }

    private fun store(context: Context, passphrase: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(passphrase)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
}
