package com.localbank.finance.ui.screens

import com.localbank.finance.ui.util.cents
import com.localbank.finance.ui.util.rememberCurrencyFormat
import com.localbank.finance.util.toReais
import com.localbank.finance.ui.util.rememberDateFormat
import com.localbank.finance.ui.util.PtBr
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.finance.ui.viewmodel.SemesterReport
import com.localbank.ui.theme.LocalAppColors
import com.localbank.finance.ui.components.BarChart
import com.localbank.finance.ui.components.BarChartEntry
import com.localbank.finance.ui.components.DonutChart
import com.localbank.finance.ui.components.DonutSlice
import com.localbank.finance.ui.components.TrendChart
import com.localbank.finance.ui.components.TrendPoint
import com.localbank.finance.ui.components.parseColor
import com.localbank.finance.ui.viewmodel.ReportViewModel
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportScreen(viewModel: ReportViewModel) {
    val state by viewModel.uiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val currency = rememberCurrencyFormat()
    val appColors = LocalAppColors.current
    val monthSdf = rememberDateFormat("MMMM yyyy")
    val isCurrentMonth = viewModel.isCurrentMonth()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Seletor de mês ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { viewModel.navigateMonth(-1) }) {
                    Icon(Icons.Default.ChevronLeft, "Mês anterior", tint = LocalAppColors.current.textSecondary)
                }
                Text(
                    text = monthSdf.format(selectedMonth.time)
                        .replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isCurrentMonth) appColors.primary else LocalAppColors.current.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { viewModel.navigateMonth(1) },
                    enabled = !isCurrentMonth
                ) {
                    Icon(Icons.Default.ChevronRight, "Próximo mês",
                        tint = if (isCurrentMonth) LocalAppColors.current.textSecondary.copy(alpha = 0.3f)
                               else LocalAppColors.current.textSecondary)
                }
            }
        }

        // Donut chart
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gastos por categoria", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        DonutChart(
                            slices = state.categoryExpenses.map {
                                DonutSlice(it.categoryName, it.totalCents.toReais(), it.colorHex)
                            }
                        )
                    }
                }
            }
        }

        // Trend chart (linha de gastos)
        if (state.monthlyComparison.any { it.expenseCents > 0 }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tendência de gastos", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        TrendChart(
                            points = state.monthlyComparison.map {
                                TrendPoint(it.monthLabel, it.expenseCents.toReais())
                            }
                        )
                    }
                }
            }
        }

        // Bar chart
        if (state.monthlyComparison.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Últimos 6 meses", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        BarChart(
                            data = state.monthlyComparison.map {
                                BarChartEntry(it.monthLabel, it.incomeCents.toReais(), it.expenseCents.toReais())
                            }
                        )
                    }
                }
            }
        }

        // Detail list
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Text("Detalhamento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
            }
            items(state.categoryExpenses, key = { it.categoryName }) { catExpense ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(parseColor(catExpense.colorHex), RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = catExpense.categoryName,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = LocalAppColors.current.textPrimary
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = currency.cents(catExpense.totalCents),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = LocalAppColors.current.textPrimary
                            )
                            Text(
                                text = String.format(PtBr, "%.1f%%", catExpense.percentage),
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Panorama semestral
        state.semesterReport?.let { sem ->
            item {
                Text("Panorama semestral", fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = LocalAppColors.current.textPrimary)
            }
            item {
                SemesterReportCard(report = sem, currency = currency)
            }
        }

        // Empty state
        if (state.categoryExpenses.isEmpty() && state.monthlyComparison.all { it.incomeCents == 0L && it.expenseCents == 0L }) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Adicione transações para ver o relatório",
                        color = LocalAppColors.current.textSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun SemesterReportCard(report: SemesterReport, currency: NumberFormat) {
    val appColors = LocalAppColors.current
    val isPositive = report.balanceCents >= 0
    val topCatColor = try { Color(android.graphics.Color.parseColor(report.topCategoryColor)) }
                      catch (_: Exception) { appColors.primary }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Título do semestre
            Text(report.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LocalAppColors.current.textPrimary)

            // Totais
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SemesterStatBox(
                    label = "Entradas",
                    value = currency.cents(report.totalIncomeCents),
                    color = LocalAppColors.current.success,
                    modifier = Modifier.weight(1f)
                )
                SemesterStatBox(
                    label = "Saídas",
                    value = currency.cents(report.totalExpenseCents),
                    color = LocalAppColors.current.error,
                    modifier = Modifier.weight(1f)
                )
                SemesterStatBox(
                    label = "Saldo",
                    value = currency.cents(report.balanceCents),
                    color = if (isPositive) LocalAppColors.current.success else LocalAppColors.current.error,
                    modifier = Modifier.weight(1f)
                )
            }

            // Taxa de economia
            if (report.totalIncomeCents > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Taxa de economia", fontSize = 13.sp, color = LocalAppColors.current.textSecondary)
                        Text(
                            "${report.savingsRate.toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (report.savingsRate >= 20) LocalAppColors.current.success else LocalAppColors.current.warning
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (report.savingsRate / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (report.savingsRate >= 20) LocalAppColors.current.success else LocalAppColors.current.warning,
                        trackColor = LocalAppColors.current.surfaceVariant
                    )
                }
            }

            HorizontalDivider(color = LocalAppColors.current.surfaceVariant)

            // Destaques
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SemesterHighlight(
                    icon = Icons.Default.EmojiEvents,
                    iconColor = LocalAppColors.current.success,
                    label = "Melhor mês",
                    value = report.bestMonth,
                    modifier = Modifier.weight(1f)
                )
                SemesterHighlight(
                    icon = Icons.Default.Warning,
                    iconColor = LocalAppColors.current.error,
                    label = "Maior gasto",
                    value = report.worstMonth,
                    modifier = Modifier.weight(1f)
                )
                SemesterHighlight(
                    icon = Icons.Default.Category,
                    iconColor = topCatColor,
                    label = "Top categoria",
                    value = report.topCategory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SemesterStatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = LocalAppColors.current.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun SemesterHighlight(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.sp, color = LocalAppColors.current.textSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textPrimary)
    }
}
