package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.ui.util.label
import com.localbank.finance.ui.util.rememberAmountFormat
import com.localbank.finance.ui.util.rememberCurrencyFormat
import com.localbank.finance.ui.util.rememberDateFormat
import com.localbank.finance.ui.util.signed
import com.localbank.finance.ui.viewmodel.BillMark
import com.localbank.finance.ui.viewmodel.DashboardUiState
import com.localbank.finance.ui.viewmodel.DashboardViewModel
import com.localbank.ui.theme.AppColors
import com.localbank.ui.theme.LocalAppColors
import java.util.Calendar
import java.util.Date

private const val DAY_MS = 24L * 60 * 60 * 1000

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onPayScheduled: (ScheduledExpense) -> Unit = {},
    onOpenReport: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val c = LocalAppColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item(key = "rhythm") { MonthRhythm(state, c) }

        val due = state.urgentExpenses + state.upcomingExpenses
        if (due.isNotEmpty()) {
            item(key = "due") {
                DueSoon(
                    expenses = due.take(5),
                    urgentIds = state.urgentExpenses.mapTo(HashSet()) { it.id },
                    categoryNames = state.categoryNames,
                    onPay = onPayScheduled,
                    c = c
                )
            }
        }

        if (state.categoryExpenses.isNotEmpty()) {
            item(key = "where") { WhereItWent(state, onOpenReport, c) }
        }

        if (state.recentTransactions.isNotEmpty()) {
            item(key = "recent") { RecentTransactions(state, c) }
        }
    }
}

// ── 1. Ritmo do mês: quanto dá para gastar por dia + régua de dias ──────────────

@Composable
private fun MonthRhythm(state: DashboardUiState, c: AppColors) {
    val currency = rememberCurrencyFormat()
    val amount = rememberAmountFormat()
    val monthName = rememberDateFormat("MMMM").format(Date()).replaceFirstChar { it.uppercase() }
    val over = state.dailyAllowance < 0

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(monthName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary,
                modifier = Modifier.semantics { heading() })
            Text("Saldo nas contas: ${currency.format(state.accountsBalance)}",
                fontSize = 13.sp, color = c.textSecondary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (over) "Passou do orçamento do mês" else "Dá para gastar",
                fontSize = 15.sp, color = if (over) c.error else c.textSecondary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val value = currency.format(kotlin.math.abs(state.dailyAllowance))
                Text(
                    value,
                    style = MaterialTheme.typography.displayLarge.let {
                        // Valores longos encolhem para caber ao lado de "por dia"
                        when {
                            value.length > 12 -> it.copy(fontSize = 34.sp, lineHeight = 40.sp)
                            value.length > 9  -> it.copy(fontSize = 42.sp, lineHeight = 48.sp)
                            else -> it
                        }
                    },
                    color = if (over) c.error else c.brandPrimary,
                    maxLines = 1
                )
                Text("por dia", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                if (over) "Faltam ${currency.format(-state.dailyAllowance * state.daysLeft)} para cobrir as contas até o fim do mês."
                else if (state.daysLeft == 1) "Hoje é o último dia do mês, já descontando as contas agendadas."
                else "Pelos próximos ${state.daysLeft} dias, já descontando as contas que vencem até o fim do mês.",
                fontSize = 14.sp, lineHeight = 20.sp, color = c.textSecondary
            )
        }

        DayStrip(state, c)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Entrou", amount.signed(state.totalIncome, true), c.success, c, Modifier.weight(1f))
            Stat("Saiu", amount.signed(state.totalExpense, false), c.error, c, Modifier.weight(1f))
            Stat("A pagar", amount.format(state.pendingThisMonth), c.textPrimary, c, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayStrip(state: DashboardUiState, c: AppColors) {
    val marks = state.billMarks
    if (marks.isEmpty()) return
    val dueDays = marks.indices.filter { marks[it] == BillMark.DUE }.map { it + 1 }
    val description = buildString {
        append("Dia ${state.today} de ${marks.size}.")
        if (dueDays.isNotEmpty()) append(" Contas a vencer nos dias ${dueDays.joinToString(", ")}.")
    }
    val past = c.textMuted.copy(alpha = 0.55f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            marks.forEachIndexed { i, mark ->
                val day = i + 1
                val isToday = day == state.today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (isToday) 40.dp else if (day < state.today) 22.dp else 30.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isToday) c.brandPrimary else if (day < state.today) past else c.surfaceVariant)
                    )
                    Box(
                        Modifier.size(5.dp).clip(CircleShape).background(
                            when (mark) {
                                BillMark.DUE  -> c.error
                                BillMark.PAID -> past
                                BillMark.NONE -> Color.Transparent
                            }
                        )
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", fontSize = 12.sp, color = c.textMuted)
            Text("Hoje, ${state.today}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Text("${marks.size}", fontSize = 12.sp, color = c.textMuted)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, c: AppColors, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

// ── 2. Vence logo ──────────────────────────────────────────────────────────────

@Composable
private fun DueSoon(
    expenses: List<ScheduledExpense>,
    urgentIds: Set<String>,
    categoryNames: Map<String, String>,
    onPay: (ScheduledExpense) -> Unit,
    c: AppColors
) {
    val amount = rememberAmountFormat()
    val weekday = rememberDateFormat("EEE")
    val dayOfMonth = rememberDateFormat("d")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Vence logo", c)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)) {
            expenses.forEachIndexed { index, expense ->
                val urgent = expense.id in urgentIds
                val tone = if (urgent) c.error else c.textPrimary
                val daysLeft = Math.floorDiv(startOfDay(expense.dueDate) - startOfDay(System.currentTimeMillis()), DAY_MS)
                val whenLabel = when {
                    daysLeft < 0  -> "atrasada"
                    daysLeft == 0L -> "hoje"
                    daysLeft == 1L -> "amanhã"
                    else -> weekday.format(Date(expense.dueDate)).trimEnd('.')
                }
                val category = expense.categoryId?.let { categoryNames[it] } ?: "Sem categoria"
                val recurrence = expense.recurrenceRule?.label?.lowercase() ?: "única"

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayOfMonth.format(Date(expense.dueDate)), fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold, color = tone, lineHeight = 22.sp)
                        Text(whenLabel, fontSize = 12.sp, color = if (urgent) c.error else c.textSecondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(expense.description.ifBlank { category }, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, color = c.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$category, $recurrence", fontSize = 13.sp, color = c.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(amount.format(expense.amount), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = c.textPrimary)
                        if (urgent) {
                            OutlinedButton(
                                onClick = { onPay(expense) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, c.brandPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.brandPrimary)
                            ) { Text("Pagar", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (index < expenses.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.surfaceVariant)
                }
            }
        }
    }
}

// ── 3. Para onde foi ───────────────────────────────────────────────────────────

@Composable
private fun WhereItWent(state: DashboardUiState, onOpenReport: () -> Unit, c: AppColors) {
    val amount = rememberAmountFormat()
    val slices = state.categoryExpenses.take(6)
    val max = slices.first().amount.takeIf { it > 0 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeading("Para onde foi", c)
            TextButton(onClick = onOpenReport) {
                Text("Relatório", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.brandPrimary)
            }
        }
        slices.forEachIndexed { i, slice ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slice.categoryName, fontSize = 14.sp, color = c.textPrimary)
                    Text(amount.format(slice.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.card)) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth((slice.amount / max).toFloat().coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            // Só a maior categoria ganha cor: é a que pede atenção
                            .background(if (i == 0) c.error else c.textMuted.copy(alpha = 0.55f))
                    )
                }
            }
        }
    }
}

// ── 4. Últimos lançamentos ─────────────────────────────────────────────────────

@Composable
private fun RecentTransactions(state: DashboardUiState, c: AppColors) {
    val amount = rememberAmountFormat()
    val shortDate = rememberDateFormat("dd/MM")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeading("Últimos lançamentos", c)
        Spacer(Modifier.height(4.dp))
        state.recentTransactions.forEach { tx ->
            val income = tx.type == TransactionType.INCOME
            val category = tx.categoryId?.let { state.categoryNames[it] } ?: "Sem categoria"
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Initial(tx.createdBy, c)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(tx.description.ifBlank { category }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$category, ${relativeDay(tx, shortDate)}", fontSize = 13.sp, color = c.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(amount.signed(tx.amount, income), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (income) c.success else c.error)
            }
        }
    }
}

/** Inicial de quem lançou — no lar compartilhado, ajuda a saber quem gastou. */
@Composable
private fun Initial(name: String?, c: AppColors) {
    val letter = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(c.surfaceVariant)
            .semantics { contentDescription = name?.let { "Lançado por $it" } ?: "Autor desconhecido" },
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    }
}

@Composable
private fun SectionHeading(text: String, c: AppColors) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary,
        modifier = Modifier.semantics { heading() })
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun relativeDay(tx: Transaction, fmt: java.text.SimpleDateFormat): String {
    val diff = Math.floorDiv(startOfDay(System.currentTimeMillis()) - startOfDay(tx.date), DAY_MS)
    return when (diff) {
        0L -> "hoje"
        1L -> "ontem"
        else -> fmt.format(Date(tx.date))
    }
}
