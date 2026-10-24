package com.localbank.finance.util

import kotlin.math.roundToLong

/**
 * Arredonda para centavos. Aplicado em todo valor que entra no app
 * (digitado, recebido do Firestore, depositado) para que o erro de ponto
 * flutuante não se acumule em saldos e somas.
 */
fun Double.roundCents(): Double = (this * 100.0).roundToLong() / 100.0
