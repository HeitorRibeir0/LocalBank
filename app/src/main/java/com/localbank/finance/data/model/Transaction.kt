package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "transactions",
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
        Index("categoryId"),
        Index("scheduledId")
    ]
)
data class Transaction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val accountId: String,          // qual conta foi usada

    val categoryId: String?,        // categoria (nullable)

    val amount: Double,             // sempre positivo — o tipo define a direção

    val type: TransactionType,      // INCOME ou EXPENSE

    val description: String,        // ex: "Almoço no restaurante"

    val date: Long,                 // timestamp em milissegundos

    val scheduledId: String? = null, // preenchido se foi gerada de uma ScheduledExpense

    val createdBy: String? = null   // preenchido na Fase 2 com nome do usuário
)