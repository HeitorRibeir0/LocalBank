package com.localbank.finance.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.localbank.finance.data.model.RecurrenceRule
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

val PtBr: Locale = Locale.forLanguageTag("pt-BR")

// Formatadores são caros de criar; sem remember eles seriam recriados a cada
// recomposição — em listas, uma vez por item visível a cada frame de scroll.

@Composable
fun rememberCurrencyFormat(): NumberFormat =
    remember { NumberFormat.getCurrencyInstance(PtBr) }

/** Valor sem "R$", com 2 casas: "1.234,50". Para listas onde a moeda é implícita. */
@Composable
fun rememberAmountFormat(): NumberFormat =
    remember {
        NumberFormat.getNumberInstance(PtBr).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

/** Formata centavos. Nunca passe centavos direto para format(): 12550 viraria "R$ 12.550,00". */
fun NumberFormat.cents(cents: Long): String = format(cents / 100.0)

/** "+1.234,50" / "−1.234,50" — o sinal acompanha a cor, para não depender só dela. */
fun NumberFormat.signed(cents: Long, positive: Boolean): String =
    (if (positive) "+" else "−") + this.cents(kotlin.math.abs(cents))

@Composable
fun rememberDateFormat(pattern: String): SimpleDateFormat =
    remember(pattern) {
        SimpleDateFormat(pattern, PtBr).apply { isLenient = false }
    }

val RecurrenceRule.label: String
    get() = when (this) {
        RecurrenceRule.DAILY   -> "Diária"
        RecurrenceRule.WEEKLY  -> "Semanal"
        RecurrenceRule.MONTHLY -> "Mensal"
        RecurrenceRule.YEARLY  -> "Anual"
    }
