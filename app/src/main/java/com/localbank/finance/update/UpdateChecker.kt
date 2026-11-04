package com.localbank.finance.update

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String
)

object UpdateChecker {

    private const val KEY_VERSION_CODE = "latest_version_code"
    private const val KEY_VERSION_NAME = "latest_version_name"
    private const val KEY_DOWNLOAD_URL = "apk_download_url"  // Mesmo nome usado no script release.yml

    suspend fun check(currentVersionCode: Int): UpdateInfo {
        return try {
            val config = FirebaseRemoteConfig.getInstance()

            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) // busca sempre (development)
                .build()
            config.setConfigSettingsAsync(settings).await()

            config.fetchAndActivate().await()

            val latestCode = config.getLong(KEY_VERSION_CODE).toInt()
            val latestName = config.getString(KEY_VERSION_NAME)
            val downloadUrl = config.getString(KEY_DOWNLOAD_URL)

            Log.d("UpdateChecker", "Current: $currentVersionCode, Latest: $latestCode, URL: $downloadUrl")

            UpdateInfo(
                hasUpdate = latestCode > currentVersionCode,
                latestVersion = latestName,
                downloadUrl = downloadUrl
            )
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Erro ao verificar atualização: ${e.message}", e)
            UpdateInfo(hasUpdate = false, latestVersion = "", downloadUrl = "")
        }
    }
}
