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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.util.*

data class DonutSlice(
    val label: String,
    val value: Double,
    val colorHex: String
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }
    if (total == 0.0) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(1000))
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 36.dp.toPx()
                var startAngle = -90f

                slices.forEach { slice ->
                    val sweep = (slice.value / total * 360f).toFloat() * animatedProgress.value
                    drawArc(
                        color = parseColor(slice.colorHex),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - strokeWidth,
                            size.height - strokeWidth
                        )
                    )
                    startAngle += sweep
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    fontSize = 12.sp,
                    color = OnDarkTextSecondary
                )
                Text(
                    text = currency.format(total),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        slices.forEach { slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(parseColor(slice.colorHex), RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = OnDarkText
                )
                Text(
                    text = currency.format(slice.value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnDarkText
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = String.format("%.0f%%", slice.value / total * 100),
                    fontSize = 12.sp,
                    color = OnDarkTextSecondary
                )
            }
        }
    }
}

internal fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
