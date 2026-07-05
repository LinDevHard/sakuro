package com.rinwave.sakuro.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.ui.theme.SakuroColors

/**
 * A custom seek bar: draggable, scrubbable and tap-to-seek, with the thumb
 * growing on drag and a gradient fill (glowMagenta → accentSakura).
 * Custom instead of the Material3 Slider API. Scrubbing is reported live;
 * the final position is committed via [onSeek].
 */
@Composable
fun SakuroSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: ((Long) -> Unit)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val playedFraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 6.dp else 3.5.dp,
        animationSpec = spring(),
        label = "trackHeight",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) 9.dp else 5.5.dp,
        animationSpec = spring(),
        label = "thumbRadius",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = f
                        onScrub?.invoke((f * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { onSeek((it * durationMs).toLong()) }
                        dragging = false
                        dragFraction = null
                    },
                    onDragCancel = {
                        dragging = false
                        dragFraction = null
                    },
                )
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((f * durationMs).toLong())
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(thumbRadius * 2)
                .align(Alignment.Center),
        ) {
            val cy = size.height / 2f
            val h = trackHeight.toPx()
            val start = thumbRadius.toPx()
            val end = size.width - thumbRadius.toPx()
            val usable = (end - start).coerceAtLeast(0f)

            // Background track.
            drawLine(
                color = SakuroColors.Twilight.copy(alpha = 0.45f),
                start = Offset(start, cy),
                end = Offset(end, cy),
                strokeWidth = h,
                cap = StrokeCap.Round,
            )
            // Buffer.
            if (bufferedFraction > 0f) {
                drawLine(
                    color = SakuroColors.TextMuted.copy(alpha = 0.40f),
                    start = Offset(start, cy),
                    end = Offset(start + usable * bufferedFraction, cy),
                    strokeWidth = h,
                    cap = StrokeCap.Round,
                )
            }
            // Played portion with the gradient.
            val playedX = start + usable * playedFraction
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(SakuroColors.GlowMagenta, SakuroColors.AccentSakura),
                    startX = start,
                    endX = end,
                ),
                start = Offset(start, cy),
                end = Offset(playedX, cy),
                strokeWidth = h,
                cap = StrokeCap.Round,
            )
            // Thumb: halo + core.
            drawCircle(
                color = SakuroColors.AccentSakura.copy(alpha = 0.25f),
                radius = thumbRadius.toPx() * 1.9f,
                center = Offset(playedX, cy),
            )
            drawCircle(
                color = SakuroColors.AccentSakura,
                radius = thumbRadius.toPx(),
                center = Offset(playedX, cy),
            )
        }
    }
}
