package com.localbank.finance.util

import kotlin.math.roundToLong

/**
 * Arredonda para centavos para evitar erro de ponto flutuante em somas.
 *
 * DEVE SER CHAMADO em:
 * - addTransaction() antes de gravar
 * - addScheduledExpense() antes de gravar
 * - Em mapTo* do FirestoreSyncManager ao receber do Firestore
 * - Em buildDashboard/buildReport ao calcular totais
 *
 * TODO (1.3): Migrar para Long em centavos para eliminar Double completamente.
 */
fun Double.roundCents(): Double = (this * 100.0).roundToLong() / 100.0

/** Converte um valor em reais para centavos (Long), arredondando corretamente. */
fun Double.toCents(): Long = (this * 100).roundToLong()
