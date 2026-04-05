package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
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
    val appColors = LocalAppColors.current
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Hero card com gradiente ──
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(appColors.gradientStart, appColors.gradientEnd)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Saldo do mês",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currency.format(state.balance),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryChip(
                            label = "Entradas",
                            value = currency.format(state.totalIncome),
                            icon = Icons.Default.ArrowUpward,
                            tint = Color(0xFF90FFCC)
                        )
                        SummaryChip(
                            label = "Saídas",
                            value = currency.format(state.totalExpense),
                            icon = Icons.Default.ArrowDownward,
                            tint = Color(0xFFFFAB91)
                        )
                    }
                }
            }
        }

        // ── Gráfico donut ──
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Gastos por categoria",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = OnDarkText
                        )
                        Spacer(Modifier.height(8.dp))
                        DonutChart(
                            slices = state.categoryExpenses.map {
                                DonutSlice(it.categoryName, it.amount, it.colorHex)
                            }
                        )
                    }
                }
            }
        }

        // ── Próximos vencimentos ──
        if (state.upcomingExpenses.isNotEmpty()) {
            item {
                Text(
                    "Próximos vencimentos",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = OnDarkText
                )
            }
            items(state.upcomingExpenses) { expense ->
                UpcomingExpenseCard(expense = expense, currency = currency)
            }
        }

        // ── Últimas transações ──
        if (state.recentTransactions.isNotEmpty()) {
            item {
                Text(
                    "Últimas transações",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = OnDarkText
                )
            }
            items(state.recentTransactions) { transaction ->
                TransactionItem(transaction = transaction, currency = currency)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun SummaryChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun UpcomingExpenseCard(expense: ScheduledExpense, currency: NumberFormat) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val daysLeft = ((expense.dueDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val isUrgent = daysLeft <= 7

    val borderColor = if (isUrgent) ExpenseRed else WarningAmber

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barra lateral colorida
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(borderColor)
            )
            Spacer(Modifier.width(12.dp))
            if (isUrgent) {
                Icon(Icons.Default.Warning, null, tint = ExpenseRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.description, fontWeight = FontWeight.Medium, color = OnDarkText)
                Text(
                    text = "Vence em ${sdf.format(Date(expense.dueDate))}",
                    fontSize = 12.sp, color = OnDarkTextSecondary
                )
            }
            Text(
                text = currency.format(expense.amount),
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, currency: NumberFormat) {
    val sdf = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    val isIncome = transaction.type == TransactionType.INCOME

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isIncome) IncomeGreen.copy(alpha = 0.12f)
                    else ExpenseRed.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = if (isIncome) IncomeGreen else ExpenseRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnDarkText
            )
            Text(
                text = sdf.format(Date(transaction.date)),
                fontSize = 12.sp,
                color = OnDarkTextSecondary
            )
        }
        Text(
            text = "${if (isIncome) "+" else "-"} ${currency.format(transaction.amount)}",
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) IncomeGreen else ExpenseRed,
            fontSize = 15.sp
        )
    }
}