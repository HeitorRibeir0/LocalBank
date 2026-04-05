package com.localbank.finance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.ui.theme.*

data class BarChartEntry(
    val label: String,
    val income: Double,
    val expense: Double
)

@Composable
fun BarChart(
    data: List<BarChartEntry>,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { maxOf(it.income, it.expense) } ?: return
    if (maxValue == 0.0) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(800))
    }

    val incomeColor = IncomeGreen
    val expenseColor = ExpenseRed

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 4.dp)
        ) {
            val groupWidth = size.width / data.size
            val barWidth = groupWidth * 0.3f
            val gap = groupWidth * 0.05f

            data.forEachIndexed { index, entry ->
                val groupStart = index * groupWidth
                val incomeX = groupStart + groupWidth * 0.175f
                val expenseX = incomeX + barWidth + gap

                val incomeHeight = (entry.income / maxValue * size.height).toFloat() * animatedProgress.value
                if (incomeHeight > 0f) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(incomeX, size.height - incomeHeight),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }

                val expenseHeight = (entry.expense / maxValue * size.height).toFloat() * animatedProgress.value
                if (expenseHeight > 0f) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(expenseX, size.height - expenseHeight),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { entry ->
                Text(
                    text = entry.label,
                    fontSize = 10.sp,
                    color = OnDarkTextSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(incomeColor, RoundedCornerShape(2.dp)))
            Text(" Entradas", fontSize = 12.sp, color = OnDarkText)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(10.dp).background(expenseColor, RoundedCornerShape(2.dp)))
            Text(" Saídas", fontSize = 12.sp, color = OnDarkText)
        }
    }
}
