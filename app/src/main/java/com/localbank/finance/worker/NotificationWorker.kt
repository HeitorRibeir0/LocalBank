package com.localbank.finance.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.repository.FinanceRepository
import java.text.NumberFormat
import java.util.*

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(context)
        val repository = FinanceRepository(
            accountDao          = db.accountDao(),
            categoryDao         = db.categoryDao(),
            transactionDao      = db.transactionDao(),
            scheduledExpenseDao = db.scheduledExpenseDao(),
            budgetDao           = db.budgetDao()
        )

        val now      = System.currentTimeMillis()
        val in7Days  = now + 7L * 24 * 60 * 60 * 1000

        val pending = repository.getPendingNotification(now, in7Days)

        if (pending.isEmpty()) return Result.success()

        createNotificationChannel()

        val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val manager  = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        pending.forEach { expense ->
            val daysLeft = ((expense.dueDate - now) / (1000 * 60 * 60 * 24)).toInt()

            val message = when {
                daysLeft <= 0 -> "${expense.description} vence hoje! (${currency.format(expense.amount)})"
                daysLeft == 1 -> "${expense.description} vence amanhã (${currency.format(expense.amount)})"
                else -> "${expense.description} vence em $daysLeft dias (${currency.format(expense.amount)})"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Despesa próxima do vencimento")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            manager.notify(expense.id.hashCode(), notification)

            // marca para não notificar de novo
            repository.markAsNotified(expense.id)
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vencimentos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de despesas próximas do vencimento"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "finance_due_dates"
    }
}