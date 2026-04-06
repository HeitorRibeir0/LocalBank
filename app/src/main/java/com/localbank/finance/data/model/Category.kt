package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Nome normalizado para exibição — garante title case mesmo em registros antigos. */
val Category.displayName: String
    get() = name.trim().lowercase().split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,           // ex: "Alimentação", "Salário"

    val icon: String,           // nome do ícone Material: "restaurant", "work"

    val colorHex: String,       // ex: "#FF6B6B" — usado nos gráficos

    val type: TransactionType   // INCOME ou EXPENSE
)

enum class TransactionType {
    INCOME,     // entrada
    EXPENSE     // saída
}
