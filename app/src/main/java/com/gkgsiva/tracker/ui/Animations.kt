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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Circular progress ring that sweeps smoothly to [progress] (0..1). */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    stroke: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
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
