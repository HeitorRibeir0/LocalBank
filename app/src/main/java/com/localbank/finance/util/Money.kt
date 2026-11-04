package com.localbank.finance.util

import kotlin.math.roundToLong

/**
 * Arredonda para centavos. Aplicado nos valores digitados (ExpenseViewModel), nos recebidos do
 * Firestore (mapTo* do FirestoreSyncManager) e nos totais do painel e do relatório.
 * A solução definitiva é guardar centavos em Long (item 1.3 do CLAUDE.md).
 */
fun Double.roundCents(): Double = (this * 100.0).roundToLong() / 100.0

/** Converte um valor em reais para centavos (Long), arredondando corretamente. */
fun Double.toCents(): Long = (this * 100).roundToLong()
