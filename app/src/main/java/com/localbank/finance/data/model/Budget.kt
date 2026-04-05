package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class Budget(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val categoryId: String,         // qual categoria este orçamento limita

    val monthYear: String,          // formato "2026-03" — qual mês esse limite se aplica

    val limitAmount: Double,        // valor máximo planejado para o mês

    val createdBy: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
