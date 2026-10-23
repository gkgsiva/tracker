package com.gkgsiva.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Text whose characters roll up, odometer style, whenever they change. */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            // Key by position from the right so "9:59:59" -> "10:00:00" rolls the right digits.
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                            (slideOutVertically(tween(250)) { -it } + fadeOut(tween(250)))
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/**
 * A filled clock face for the 8h target: a wedge in [fillColor] sweeps clockwise from 12 o'clock
 * as [progress] (0..1) grows, with a hand at its edge and one tick per hour.
 */
@Composable
fun ClockDial(
    progress: Float,
    fillColor: Color,
    faceColor: Color,
    handColor: Color,
    tickColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    hours: Int = 8,
) {
    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "dial",
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        fun point(fraction: Float, radius: Float): Offset {
            val a = Math.toRadians(-90.0 + 360.0 * fraction)
            return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        }

        drawCircle(faceColor, r)
        drawArc(fillColor, -90f, 360f * sweep, useCenter = true)
        for (i in 0 until hours) {
            drawLine(
                tickColor,
                point(i / hours.toFloat(), r * 0.8f),
                point(i / hours.toFloat(), r * 0.95f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawLine(handColor, center, point(sweep, r * 0.72f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(handColor, 7.dp.toPx())
        drawCircle(handColor.copy(alpha = 0.25f), r, style = Stroke(2.dp.toPx()))
    }
}

/** A small dot that gently pulses, used as a "live" indicator. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier.size(10.dp).scale(scale).alpha(alpha).background(color, CircleShape))
}
