package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "scheduled_expenses",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("accountId"),
        Index("categoryId")
    ]
)
data class ScheduledExpense(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val accountId: String,

    val categoryId: String?,

    val amount: Double,

    val description: String,        // ex: "Aluguel", "Netflix"

    val dueDate: Long,              // data de vencimento (timestamp)

    // --- recorrência ---
    val isRecurring: Boolean = false,
    val recurrenceRule: RecurrenceRule? = null,

    // --- estado ---
    val isPaid: Boolean = false,
    val notified: Boolean = false,

    val createdBy: String? = null   // preenchido na Fase 2 com nome do usuário
)

enum class RecurrenceRule {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
