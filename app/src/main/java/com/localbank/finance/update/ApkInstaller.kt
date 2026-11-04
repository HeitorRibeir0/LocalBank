package com.localbank.finance.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object ApkInstaller {

    private const val APK_DIR = "updates"
    private const val APK_FILENAME = "update.apk"

    suspend fun downloadAndInstall(context: Context, downloadUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val apkFile = downloadApk(context, downloadUrl)
            installApk(context, apkFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadApk(context: Context, url: String): File {
        val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
        val file = File(dir, APK_FILENAME)

        URL(url).openStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: precisa ser a intent do installer
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
        }

        context.startActivity(intent)
    }
}
