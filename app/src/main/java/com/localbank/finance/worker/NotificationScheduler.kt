package com.localbank.finance.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val WORK_NAME = "finance_notification_check"

    // chama isso uma vez na MainActivity — o WorkManager garante que continua
    // rodando mesmo após fechar o app ou reiniciar o celular
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // não roda com bateria crítica
            .build()

        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS  // verifica uma vez por dia
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // se já existe, não recria
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}