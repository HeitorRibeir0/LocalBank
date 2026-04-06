package com.localbank.finance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.*

@Composable
fun ReportScreen(viewModel: ReportViewModel) {
    val state by viewModel.uiState.collectAsState()
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Relatório do mês", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OnDarkText)
        }

        // Donut chart
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gastos por categoria", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                        Spacer(Modifier.height(12.dp))
                        DonutChart(
                            slices = state.categoryExpenses.map {
                                DonutSlice(it.categoryName, it.totalAmount, it.colorHex)
                            }
                        )
                    }
                }
            }
        }

        // Trend chart (linha de gastos)
        if (state.monthlyComparison.any { it.expense > 0 }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tendência de gastos", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                        Spacer(Modifier.height(12.dp))
                        TrendChart(
                            points = state.monthlyComparison.map {
                                TrendPoint(it.monthLabel, it.expense)
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
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Últimos 6 meses", fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, color = OnDarkText)
                        Spacer(Modifier.height(12.dp))
                        BarChart(
                            data = state.monthlyComparison.map {
                                BarChartEntry(it.monthLabel, it.income, it.expense)
                            }
                        )
                    }
                }
            }
        }

        // Detail list
        if (state.categoryExpenses.isNotEmpty()) {
            item {
                Text("Detalhamento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OnDarkText)
            }
            items(state.categoryExpenses) { catExpense ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
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
                            color = OnDarkText
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = currency.format(catExpense.totalAmount),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = OnDarkText
                            )
                            Text(
                                text = String.format("%.1f%%", catExpense.percentage),
                                fontSize = 12.sp,
                                color = OnDarkTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Empty state
        if (state.categoryExpenses.isEmpty() && state.monthlyComparison.all { it.income == 0.0 && it.expense == 0.0 }) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Adicione transações para ver o relatório",
                        color = OnDarkTextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
