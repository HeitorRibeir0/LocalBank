package com.localbank.finance.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Compra detectada numa notificação, aguardando confirmação.
 * Fica só neste aparelho (não vai para o lar) até você confirmar.
 * O texto bruto da notificação não é guardado.
 */
@Entity(
    tableName = "pending_captures",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class PendingCapture(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val amount: Double,          // valor da compra, sempre positivo

    val merchant: String,        // estabelecimento lido da notificação

    val sourceLabel: String,     // ex: "Nubank"

    val sourcePackage: String,   // pacote do app de origem

    val detectedAt: Long,        // hora da notificação (ms)

    val fingerprint: String      // evita duplicar a mesma compra
)
