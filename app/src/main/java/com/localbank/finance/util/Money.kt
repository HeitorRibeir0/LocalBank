package com.localbank.finance.util

import kotlin.math.roundToLong

// Dinheiro circula em centavos (Long). Double só nas bordas: formatação e Firestore legado.

fun Double.toCents(): Long = (this * 100).roundToLong()

fun Long.toReais(): Double = this / 100.0
