package com.localbank.finance.update

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
    private const val KEY_DOWNLOAD_URL = "apk_download_url"

    suspend fun check(currentVersionCode: Int): UpdateInfo {
        val config = FirebaseRemoteConfig.getInstance()

        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(600) // busca no máximo a cada 10 minutos
            .build()
        config.setConfigSettingsAsync(settings).await()

        // Valores padrão caso o Remote Config não esteja configurado ainda
        config.setDefaultsAsync(
            mapOf(
                KEY_VERSION_CODE to currentVersionCode.toLong(),
                KEY_VERSION_NAME to "1.0",
                KEY_DOWNLOAD_URL to ""
            )
        ).await()

        config.fetchAndActivate().await()

        val latestCode = config.getLong(KEY_VERSION_CODE).toInt()
        val latestName = config.getString(KEY_VERSION_NAME)
        val downloadUrl = config.getString(KEY_DOWNLOAD_URL)

        return UpdateInfo(
            hasUpdate = latestCode > currentVersionCode,
            latestVersion = latestName,
            downloadUrl = downloadUrl
        )
    }
}
