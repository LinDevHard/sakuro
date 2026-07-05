package com.rinwave.sakuro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.ui.theme.SakuroColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The eclipse logo mark (BRAND.md §3): a rotating arc-ring over a dim ring rim.
 * Used as a loading/buffering indicator.
 */
@Composable
fun EclipseLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val transition = rememberInfiniteTransition(label = "eclipse")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    // Comet-head pulse, independent of the rotation.
    val flarePulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flare",
    )

    Canvas(modifier.size(size)) {
        val strokePx = this.size.minDimension * 0.055f
        val radius = (this.size.minDimension - strokePx * 2.4f) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Dim ring rim.
        drawCircle(
            color = SakuroColors.Twilight.copy(alpha = 0.30f),
            radius = radius,
            center = center,
            style = Stroke(width = strokePx),
        )

        // Rotating arc: a gap (near 0 at the tail), the head ending in the brightest sector.
        rotate(degrees = angle, pivot = center) {
            val brush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.18f to Color.Transparent,
                0.72f to SakuroColors.GlowMagenta.copy(alpha = 0.65f),
                1.00f to SakuroColors.AccentSakura,
                center = center,
            )
            drawArc(
                brush = brush,
                startAngle = -300f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        // Comet head at the end of the arc.
        val rad = angle * (PI / 180f)
        val head = Offset(
            x = center.x + cos(rad).toFloat() * radius,
            y = center.y + sin(rad).toFloat() * radius,
        )
        drawCircle(
            color = SakuroColors.AccentSakura.copy(alpha = 0.35f * flarePulse),
            radius = strokePx * 2.2f,
            center = head,
        )
        drawCircle(
            color = SakuroColors.TextPrimary.copy(alpha = flarePulse),
            radius = strokePx * 0.75f,
            center = head,
        )
    }
}
