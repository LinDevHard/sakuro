package com.rinwave.sakuro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatTime

/**
 * Отрисовка «кадра» фейкового движка: сумеречный градиент и кольцо-«затмение»
 * из бренда (BRAND.md §3) как индикатор прогресса.
 */
@Composable
fun FakeVideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier) {
    val state by engine.state.collectAsState()
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    SakuroColors.Background,
                    SakuroColors.Twilight,
                    SakuroColors.Background,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Canvas(Modifier.size(140.dp)) {
                drawCircle(
                    color = SakuroColors.Twilight,
                    style = Stroke(width = 6.dp.toPx()),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(SakuroColors.GlowMagenta, SakuroColors.AccentSakura, SakuroColors.GlowMagenta),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx()),
                )
                drawCircle(
                    color = SakuroColors.AccentSakura,
                    radius = 5.dp.toPx(),
                    center = Offset(size.width / 2f, 0f + 3.dp.toPx()),
                )
            }
            Text(
                text = formatTime(state.positionMs) + " / " + formatTime(state.durationMs),
                color = SakuroColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "FAKE ENGINE — полигон UI",
                color = SakuroColors.TextMuted,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}
