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
import com.localbank.finance.util.DateRanges
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
            budgetDao           = db.budgetDao(),
            savingsGoalDao      = db.savingsGoalDao()
        )

        val now = System.currentTimeMillis()
        val pending = repository.getPendingNotification(now + 4L * 24 * 60 * 60 * 1000)

        // Estágio 1: faltam 1 a 3 dias. Estágio 2: vence hoje ou já venceu.
        val toNotify = pending.mapNotNull { expense ->
            val days = DateRanges.daysUntil(expense.dueDate, now)
            when {
                days <= 0 && expense.notifiedStage < 2 -> Triple(expense, days, 2)
                days in 1..3 && expense.notifiedStage < 1 -> Triple(expense, days, 1)
                else -> null
            }
        }
        if (toNotify.isEmpty()) return Result.success()

        createNotificationChannel()

        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        val manager  = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        toNotify.forEach { (expense, days, stage) ->
            val value = currency.format(expense.amount)
            val message = when {
                days < 0  -> "${expense.description} está atrasada ($value)"
                days == 0 -> "${expense.description} vence hoje! ($value)"
                days == 1 -> "${expense.description} vence amanhã ($value)"
                else      -> "${expense.description} vence em $days dias ($value)"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Despesa próxima do vencimento")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            manager.notify(expense.id.hashCode(), notification)
            repository.setNotifiedStage(expense.id, stage)
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