package com.localbank.finance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

data class TrendPoint(val label: String, val value: Double)

@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty() || points.all { it.value == 0.0 }) return

    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val maxValue = points.maxOf { it.value }.takeIf { it > 0 } ?: return
    val minValue = points.minOf { it.value }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(1000))
    }

    // Calcula tendência (regressão linear simples)
    val n = points.size
    val xMean = (n - 1) / 2.0
    val yMean = points.map { it.value }.average()
    val slope = if (n > 1) {
        val num = points.indices.sumOf { i -> (i - xMean) * (points[i].value - yMean) }
        val den = points.indices.sumOf { i -> (i - xMean) * (i - xMean) }
        if (den != 0.0) num / den else 0.0
    } else 0.0

    val trendColor = when {
        slope > yMean * 0.05 -> ExpenseRed       // subindo mais de 5%
        slope < -yMean * 0.05 -> IncomeGreen     // caindo mais de 5%
        else -> WarningAmber                      // estável
    }

    val trendLabel = when {
        slope > yMean * 0.05 -> "Em alta"
        slope < -yMean * 0.05 -> "Em queda"
        else -> "Estável"
    }

    val trendIcon = when {
        slope > yMean * 0.05 -> Icons.Default.TrendingUp
        slope < -yMean * 0.05 -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }

    Column(modifier = modifier) {
        // Badge de tendência
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(18.dp))
            Text(trendLabel, color = trendColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                " · média ${currency.format(yMean)}/mês",
                color = OnDarkTextSecondary, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 8.dp)
        ) {
            val range = (maxValue - minValue).takeIf { it > 0 } ?: maxValue
            val padding = size.height * 0.1f

            fun xFor(i: Int) = if (n == 1) size.width / 2
                               else i * (size.width / (n - 1).toFloat())

            fun yFor(v: Double) = (size.height - padding) -
                ((v - minValue) / range * (size.height - padding * 2)).toFloat()

            // Preenche a área sob a linha animada
            val fillPath = Path()
            val cutIndex = ((n - 1) * animatedProgress.value).toInt()
            val frac = ((n - 1) * animatedProgress.value) - cutIndex

            fillPath.moveTo(xFor(0), size.height)
            for (i in 0..cutIndex) {
                fillPath.lineTo(xFor(i), yFor(points[i].value))
            }
            if (cutIndex < n - 1) {
                val x = xFor(cutIndex) + (xFor(cutIndex + 1) - xFor(cutIndex)) * frac
                val y = yFor(points[cutIndex].value) +
                        (yFor(points[cutIndex + 1].value) - yFor(points[cutIndex].value)) * frac
                fillPath.lineTo(x, y)
                fillPath.lineTo(x, size.height)
            } else {
                fillPath.lineTo(xFor(cutIndex), size.height)
            }
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(trendColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f, endY = size.height
                )
            )

            // Linha principal animada
            val linePath = Path()
            linePath.moveTo(xFor(0), yFor(points[0].value))
            for (i in 1..cutIndex) {
                linePath.lineTo(xFor(i), yFor(points[i].value))
            }
            if (cutIndex < n - 1) {
                val x = xFor(cutIndex) + (xFor(cutIndex + 1) - xFor(cutIndex)) * frac
                val y = yFor(points[cutIndex].value) +
                        (yFor(points[cutIndex + 1].value) - yFor(points[cutIndex].value)) * frac
                linePath.lineTo(x, y)
            }

            drawPath(
                path = linePath,
                color = trendColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Pontos
            for (i in 0..cutIndex) {
                drawCircle(
                    color = trendColor,
                    radius = 5.dp.toPx(),
                    center = Offset(xFor(i), yFor(points[i].value))
                )
                drawCircle(
                    color = DarkCard,
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(i), yFor(points[i].value))
                )
            }
        }

        // Labels dos meses
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(point.label, fontSize = 10.sp, color = OnDarkTextSecondary)
            }
        }
    }
}
