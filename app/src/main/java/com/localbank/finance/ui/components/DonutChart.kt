package com.localbank.finance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private const val MAX_SLICES = 4
private const val OUTROS_COLOR = "#94A3B8"
private const val GAP_DEGREES = 2f

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }
    if (total == 0.0) return

    // Group: top MAX_SLICES + "Outros" for the rest
    val sorted = slices.sortedByDescending { it.value }
    val top = sorted.take(MAX_SLICES)
    val rest = sorted.drop(MAX_SLICES)
    val consolidated: List<DonutSlice> = if (rest.isEmpty()) top
    else top + DonutSlice("Outros", rest.sumOf { it.value }, OUTROS_COLOR)

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(consolidated) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(900))
    }

    var selectedIndex by remember(consolidated) { mutableStateOf<Int?>(null) }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Donut ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val strokeWidth = 32.dp.toPx()
                val selectedStrokeWidth = 38.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - strokeWidth,
                    size.height - strokeWidth
                )
                val arcSizeSelected = androidx.compose.ui.geometry.Size(
                    size.width - selectedStrokeWidth + 4.dp.toPx(),
                    size.height - selectedStrokeWidth + 4.dp.toPx()
                )

                var startAngle = -90f
                consolidated.forEachIndexed { index, slice ->
                    val rawSweep = (slice.value / total * 360f).toFloat()
                    val gap = if (consolidated.size > 1) GAP_DEGREES else 0f
                    val sweep = (rawSweep - gap).coerceAtLeast(0f) * animatedProgress.value

                    val isSelected = selectedIndex == index
                    val isOtherSelected = selectedIndex != null && !isSelected
                    val alpha = when {
                        isSelected -> 1f
                        isOtherSelected -> 0.35f
                        else -> 1f
                    }
                    val sw = if (isSelected) selectedStrokeWidth else strokeWidth
                    val offset = if (isSelected)
                        androidx.compose.ui.geometry.Offset(
                            (strokeWidth - selectedStrokeWidth) / 2 - 2.dp.toPx(),
                            (strokeWidth - selectedStrokeWidth) / 2 - 2.dp.toPx()
                        )
                    else
                        androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)

                    drawArc(
                        color = parseColor(slice.colorHex).copy(alpha = alpha),
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft = if (isSelected) offset else androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                        size = if (isSelected) arcSizeSelected else arcSize
                    )
                    startAngle += rawSweep
                }
            }

            // Center label
            val active = selectedIndex?.let { consolidated.getOrNull(it) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active != null) {
                    Text(
                        text = String.format("%.0f%%", active.value / total * 100),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkText
                    )
                    Text(
                        text = active.label,
                        fontSize = 11.sp,
                        color = OnDarkTextSecondary
                    )
                } else {
                    Text(
                        text = currency.format(total),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkText
                    )
                    Text(
                        text = "total",
                        fontSize = 11.sp,
                        color = OnDarkTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Legend ─────────────────────────────────────────────────────────
        consolidated.forEachIndexed { index, slice ->
            val isSelected = selectedIndex == index
            val isOtherSelected = selectedIndex != null && !isSelected
            val rowAlpha = if (isOtherSelected) 0.4f else 1f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selectedIndex = if (isSelected) null else index
                    }
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(parseColor(slice.colorHex).copy(alpha = rowAlpha))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = slice.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = OnDarkText.copy(alpha = rowAlpha)
                )
                Text(
                    text = currency.format(slice.value),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = OnDarkText.copy(alpha = rowAlpha)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = String.format("%.0f%%", slice.value / total * 100),
                    fontSize = 12.sp,
                    color = OnDarkTextSecondary.copy(alpha = rowAlpha),
                    modifier = Modifier.width(36.dp),
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
