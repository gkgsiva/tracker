package com.gkgsiva.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.max

/** One bar: [inside] hours stacked under [breaks] hours; [lop] bars are drawn in the error color. */
data class Bar(val label: String, val inside: Float, val breaks: Float, val lop: Boolean = false)

/** Stacked bar chart in hours with a dashed target line. */
@Composable
fun BarChart(bars: List<Bar>, target: Float, modifier: Modifier = Modifier) {
    val insideColor = MaterialTheme.colorScheme.primary
    val breakColor = MaterialTheme.colorScheme.tertiary
    val lopColor = MaterialTheme.colorScheme.error
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val axisWidth = 28.dp.toPx()
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val chartWidth = size.width - axisWidth
        val maxValue = max(target + 1f, bars.maxOfOrNull { it.inside + it.breaks } ?: 0f)
        val top = ceil(maxValue / 2f) * 2f
        fun y(v: Float) = chartHeight * (1f - v / top)

        var tick = 0f
        while (tick <= top) {
            val ty = y(tick)
            drawLine(gridColor, Offset(axisWidth, ty), Offset(size.width, ty), strokeWidth = 1f)
            val text = measurer.measure("${tick.toInt()}h", labelStyle)
            drawText(text, topLeft = Offset(0f, ty - text.size.height / 2f))
            tick += 2f
        }

        if (bars.isNotEmpty()) {
            val slot = chartWidth / bars.size
            val barWidth = slot * 0.6f
            val corner = CornerRadius(3.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val left = axisWidth + i * slot + (slot - barWidth) / 2f
                if (bar.inside > 0f) {
                    drawRoundRect(if (bar.lop) lopColor else insideColor, Offset(left, y(bar.inside)), Size(barWidth, chartHeight - y(bar.inside)), corner)
                }
                if (bar.breaks > 0f) {
                    val bTop = y(bar.inside + bar.breaks)
                    drawRoundRect(breakColor, Offset(left, bTop), Size(barWidth, y(bar.inside) - bTop), corner)
                }
                val text = measurer.measure(bar.label, labelStyle)
                drawText(text, topLeft = Offset(left + barWidth / 2f - text.size.width / 2f, chartHeight + 4.dp.toPx()))
            }
        }

        drawLine(
            targetColor,
            Offset(axisWidth, y(target)),
            Offset(size.width, y(target)),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        )
    }
}
