package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val colorHex: String = "#4CAF50",
    val deadline: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null
)
