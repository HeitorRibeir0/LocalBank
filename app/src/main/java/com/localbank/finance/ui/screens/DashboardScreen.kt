package com.localbank.finance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.ui.components.CardTokens
import com.localbank.finance.ui.components.DonutChart
import com.localbank.finance.ui.components.DonutSlice
import com.localbank.finance.ui.viewmodel.DashboardViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val c = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val isPositive = state.balance >= 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {

        // ── 1. BLOCO DOMINANTE — saldo + status ──────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(c.gradientStart, c.gradientEnd))
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {

                    // Status positivo/negativo — primeira coisa que o olho lê
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp
                                          else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPositive) "Positivo este mês" else "Negativo este mês",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Valor principal — centro visual
                    Text(
                        text = currency.format(state.balance),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        textAlign = TextAlign.Center
                    )

                    // Saldo projetado — suporte, sem competir
                    if (state.pendingScheduledTotal > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Projetado: ${currency.format(state.projectedBalance)}",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Entradas vs Saídas — peso secundário
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStat(
                            label = "Entradas",
                            value = currency.format(state.totalIncome),
                            icon = Icons.Default.ArrowUpward,
                            tint = Color.White.copy(alpha = 0.65f),
                            valueTint = Color.White.copy(alpha = 0.9f)
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        MiniStat(
                            label = "Saídas",
                            value = currency.format(state.totalExpense),
                            icon = Icons.Default.ArrowDownward,
                            tint = Color.White.copy(alpha = 0.65f),
                            valueTint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // ── 2. ALERTAS — só aparece se há algo urgente ───────────────────────
        val urgentExpenses = state.upcomingExpenses.filter {
            val daysLeft = ((it.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
            daysLeft <= 3
        }
        if (urgentExpenses.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitle("Atenção necessária", c.error)
                    urgentExpenses.forEach { expense ->
                        AlertCard(expense = expense, currency = currency, colors = c)
                    }
                }
            }
        }

        // ── 3. PRÓXIMOS VENCIMENTOS (não urgentes) ───────────────────────────
        val normalUpcoming = state.upcomingExpenses.filter { expense ->
            val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
            daysLeft > 3
        }
        if (normalUpcoming.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitle("Próximos vencimentos", c.textPrimary)
                    normalUpcoming.forEach { expense ->
                        UpcomingExpenseCard(expense = expense, currency = currency)
                    }
                }
            }
        }

        // ── 4. GASTOS POR CATEGORIA ───────────────────────────────────────────
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(CardTokens.radius),
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    elevation = CardDefaults.cardElevation(CardTokens.elevation),
                    border = CardTokens.border
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionTitle("Gastos por categoria", c.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        DonutChart(
                            slices = state.categoryExpenses.map {
                                DonutSlice(it.categoryName, it.amount, it.colorHex)
                            }
                        )
                    }
                }
            }
        }

        // ── 5. ÚLTIMAS TRANSAÇÕES ─────────────────────────────────────────────
        if (state.recentTransactions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Últimas transações", c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = c.card),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            state.recentTransactions.forEachIndexed { index, transaction ->
                                TransactionItem(transaction = transaction, currency = currency)
                                if (index < state.recentTransactions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = c.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,   // 18sp SemiBold
        color = color
    )
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    valueTint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = tint, fontSize = 11.sp, letterSpacing = 0.3.sp)
            Text(value, color = valueTint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AlertCard(expense: ScheduledExpense, currency: NumberFormat, colors: AppColors) {
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val isOverdue = daysLeft < 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.error.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning, null,
                tint = colors.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description.ifBlank { "Despesa" },
                    style = MaterialTheme.typography.bodyLarge,  // 16sp
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isOverdue) "Vencida há ${-daysLeft} dias"
                    else "Vence hoje${if (daysLeft == 1) " amanhã" else ""}",
                    fontSize = 12.sp,
                    color = colors.error
                )
            }
            Text(
                currency.format(expense.amount),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.error
            )
        }
    }
}

@Composable
fun UpcomingExpenseCard(expense: ScheduledExpense, currency: NumberFormat) {
    val c = LocalAppColors.current
    val sdf = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val accentColor = if (daysLeft <= 7) c.warning else c.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = expense.description.ifBlank { "Despesa agendada" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,   // 14sp
            color = c.textPrimary
        )
        Text(
            text = sdf.format(Date(expense.dueDate)),
            fontSize = 12.sp,
            color = accentColor,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = currency.format(expense.amount),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = c.textPrimary
        )
    }
}

@Composable
fun TransactionItem(transaction: Transaction, currency: NumberFormat) {
    val c = LocalAppColors.current
    val sdf = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) c.success else c.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícone compacto
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isIncome) c.success.copy(alpha = 0.12f)
                    else c.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Default.ArrowUpward
                              else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = if (isIncome) c.success else c.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description.ifBlank { if (isIncome) "Entrada" else "Saída" },
                style = MaterialTheme.typography.bodyMedium,   // 14sp
                color = c.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = sdf.format(Date(transaction.date)),
                fontSize = 12.sp,
                color = c.textSecondary
            )
        }
        Text(
            text = "${if (isIncome) "+" else "−"} ${currency.format(transaction.amount)}",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = amountColor
        )
    }
}
