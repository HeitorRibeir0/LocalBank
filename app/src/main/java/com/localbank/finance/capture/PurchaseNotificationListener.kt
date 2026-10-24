package com.localbank.finance.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.firebase.auth.FirebaseAuth
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.model.PendingCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lê as notificações de compra dos apps listados em [CaptureSources] e guarda
 * o que encontrar como "pendente". Nada é lançado sozinho: você confirma depois.
 * Só guarda valor, estabelecimento e origem.
 */
class PurchaseNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = CaptureSources.labelFor(sbn.packageName) ?: return
        // Sem usuário logado não há lar para receber a compra: ignora
        if (FirebaseAuth.getInstance().currentUser == null) return

        val extras = sbn.notification.extras
        val text = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .joinToString(" ") { key -> extras.getCharSequence(key)?.toString().orEmpty() }

        val parsed = PurchaseParser.parse(text) ?: return
        val detectedAt = sbn.postTime

        val capture = PendingCapture(
            amount = parsed.amountCents / 100.0,
            merchant = parsed.merchant,
            sourceLabel = label,
            sourcePackage = sbn.packageName,
            detectedAt = detectedAt,
            // Mesmo app + mesmo valor + mesmo estabelecimento em 5 minutos = mesma compra
            fingerprint = "${sbn.packageName}|${parsed.amountCents}|" +
                "${parsed.merchant.lowercase()}|${detectedAt / (5L * 60 * 1000)}"
        )

        scope.launch {
            AppDatabase.getInstance(applicationContext).pendingCaptureDao().insert(capture)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
