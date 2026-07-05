package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.navigation.PlayerComponent
import com.rinwave.sakuro.ui.theme.SakuroColors

/** «Stats for nerds» (FEATURES.md §4): моноширинный ненавязчивый оверлей. */
@Composable
fun DebugOverlay(component: PlayerComponent, modifier: Modifier = Modifier) {
    val stats by component.engine.debugStats.collectAsState(initial = DebugStats())
    val state by component.engine.state.collectAsState()
    val detection by component.detection.collectAsState()
    val adaptive by component.adaptiveDecision.collectAsState()
    val adaptiveEnabled by component.adaptiveEnabled.collectAsState()

    val lines = buildList {
        add("engine    ${stats.engine}")
        stats.videoDecoder?.let { add("decoder   $it") }
        stats.videoCodec?.let { add("vcodec    $it") }
        val src = stats.sourceResolution
        val out = stats.outputResolution
        if (src != null) add("video     $src → ${out ?: src}")
        stats.videoFps?.let { add("fps       $it") }
        add("dropped   ${stats.droppedFrames}")
        stats.bitrateKbps?.let { add("bitrate   $it kbps") }
        stats.colorInfo?.let { add("color     $it") }
        stats.audioCodec?.let {
            add("audio     $it ${stats.audioChannels ?: "?"}ch ${stats.audioSampleRateHz ?: "?"}Hz")
        }
        add("preset    ${stats.upscaleProfile ?: "—"}")
        stats.upscalePasses.forEach { add("  pass    $it") }
        val confidencePercent = (detection.confidence * 100).toInt()
        add("detect    ${detection.contentClass.name.lowercase()} $confidencePercent% (${detection.source})")
        when {
            // Тумблер выключен — пресет применяется как есть (чистота тестов).
            !adaptiveEnabled -> add("adaptive  off (пресет как есть)")
            else -> adaptive?.takeIf { it.level > 0 }?.let { add("adaptive  L${it.level} ${it.reason.orEmpty()}") }
        }
        stats.extras.forEach { (k, v) -> add("$k  $v") }
        add("status    ${state.status} speed=${state.speed}x")
    }

    Column(
        modifier
            .background(SakuroColors.Background.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = SakuroColors.TextPrimary,
            )
        }
    }
}
