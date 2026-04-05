package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,           // ex: "Carteira", "Nubank"

    val type: AccountType,      // CHECKING, SAVINGS, WALLET

    val balance: Double,        // saldo atual (atualizado a cada transação)

    val currency: String = "BRL",

    val createdAt: Long = System.currentTimeMillis()
)

enum class AccountType {
    CHECKING,   // conta corrente
    SAVINGS,    // poupança
    WALLET      // carteira física
}
