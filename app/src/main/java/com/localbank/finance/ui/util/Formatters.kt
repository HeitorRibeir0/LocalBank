package com.localbank.finance.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.localbank.finance.data.model.RecurrenceRule
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

val PtBr: Locale = Locale("pt", "BR")

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

/** "+1.234,50" / "−1.234,50" — o sinal acompanha a cor, para não depender só dela. */
fun NumberFormat.signed(value: Double, positive: Boolean): String =
    (if (positive) "+" else "−") + format(kotlin.math.abs(value))

@Composable
fun rememberDateFormat(pattern: String): SimpleDateFormat =
    remember(pattern) { SimpleDateFormat(pattern, PtBr) }

val RecurrenceRule.label: String
    get() = when (this) {
        RecurrenceRule.DAILY   -> "Diária"
        RecurrenceRule.WEEKLY  -> "Semanal"
        RecurrenceRule.MONTHLY -> "Mensal"
        RecurrenceRule.YEARLY  -> "Anual"
    }
