package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.rinwave.sakuro.bench.BenchStage
import com.rinwave.sakuro.bench.BenchState
import com.rinwave.sakuro.bench.BenchSurface
import com.rinwave.sakuro.bench.rememberBenchRunner
import com.rinwave.sakuro.navigation.BenchComponent
import com.rinwave.sakuro.ui.displayName
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.rememberVideoFilePicker
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.bench_cancel
import sakuro.composeapp.generated.resources.bench_done
import sakuro.composeapp.generated.resources.bench_done_desc
import sakuro.composeapp.generated.resources.bench_engines
import sakuro.composeapp.generated.resources.bench_estimate
import sakuro.composeapp.generated.resources.bench_failed
import sakuro.composeapp.generated.resources.bench_pick_video
import sakuro.composeapp.generated.resources.bench_presets
import sakuro.composeapp.generated.resources.bench_presets_hint
import sakuro.composeapp.generated.resources.bench_progress
import sakuro.composeapp.generated.resources.bench_run
import sakuro.composeapp.generated.resources.bench_share
import sakuro.composeapp.generated.resources.bench_stage_capturing
import sakuro.composeapp.generated.resources.bench_stage_packing
import sakuro.composeapp.generated.resources.bench_stage_playing
import sakuro.composeapp.generated.resources.bench_stage_starting
import sakuro.composeapp.generated.resources.bench_unsupported
import sakuro.composeapp.generated.resources.bench_video

/**
 * Benchmark setup and progress: pick a video, choose the engine × preset
 * matrix, run it, then share the resulting bundle with `tools/sakuro-bench`.
 */
@Composable
internal fun BenchScreen(component: BenchComponent) {
    val runner = rememberBenchRunner(component.engineRegistry)
    if (runner == null) {
        Text(
            stringResource(Res.string.bench_unsupported),
            color = SakuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val state by runner.state.collectAsState()
    val video by component.video.collectAsState()
    val presets by component.presets.collectAsState()
    val selectedEngines by component.selectedEngines.collectAsState()
    val selectedPresets by component.selectedPresets.collectAsState()
    val pickVideo = rememberVideoFilePicker(component::setVideo)
    val running = state is BenchState.Running

    // The bench renders here and captures come from this surface, so it stays
    // composed for the whole screen — not only while a run is active.
    BenchSurface(
        runner,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(SakuroColors.Surface),
    )

    SettingsPanel(title = stringResource(Res.string.bench_video), icon = Lucide.Film) {
        Text(
            video?.title ?: stringResource(Res.string.bench_pick_video),
            color = if (video == null) SakuroColors.TextMuted else SakuroColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(onClick = pickVideo, enabled = !running, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(Res.string.bench_pick_video), color = SakuroColors.AccentSakura)
        }
    }

    SettingsPanel(title = stringResource(Res.string.bench_engines), icon = Lucide.Cpu) {
        ChipFlow(Modifier.padding(horizontal = 16.dp)) {
            component.engines.forEach { engine ->
                BenchChip(
                    label = engine.label(),
                    selected = engine in selectedEngines,
                    enabled = !running,
                    onClick = { component.toggleEngine(engine) },
                )
            }
        }
    }

    SettingsPanel(
        title = stringResource(Res.string.bench_presets),
        icon = Lucide.Sparkles,
        subtitle = stringResource(Res.string.bench_presets_hint),
    ) {
        ChipFlow(Modifier.padding(horizontal = 16.dp)) {
            presets.forEach { preset ->
                BenchChip(
                    label = preset.displayName(),
                    selected = preset.id in selectedPresets,
                    enabled = !running,
                    onClick = { component.togglePreset(preset.id) },
                )
            }
        }
    }

    val configCount = selectedEngines.size * selectedPresets.size
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        when (val current = state) {
            is BenchState.Running -> RunningRow(current, onCancel = runner::cancel)

            is BenchState.Done -> {
                Text(
                    stringResource(Res.string.bench_done, current.bundleName),
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.bench_done_desc, current.configCount, current.captureCount),
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { runner.share(current.bundlePath) },
                    colors = ButtonDefaults.buttonColors(containerColor = SakuroColors.GlowMagenta),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(Res.string.bench_share))
                }
            }

            is BenchState.Failed -> Text(
                stringResource(Res.string.bench_failed, current.message),
                color = SakuroColors.AccentSakura,
                style = MaterialTheme.typography.bodyMedium,
            )

            BenchState.Idle -> Unit
        }

        if (!running) {
            Text(
                stringResource(Res.string.bench_estimate, configCount, estimateMinutes(configCount)),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { video?.let { runner.start(it, component.buildConfigs()) } },
                enabled = video != null && configCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SakuroColors.GlowMagenta),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(Res.string.bench_run))
            }
        }
    }
}

@Composable
private fun RunningRow(state: BenchState.Running, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = SakuroColors.AccentSakura,
            strokeWidth = 2.dp,
            modifier = Modifier.padding(end = 12.dp).then(Modifier),
        )
        Text(
            stringResource(
                Res.string.bench_progress,
                state.configIndex,
                state.configCount,
                state.mode,
                state.stage.label(),
            ),
            color = SakuroColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(Res.string.bench_cancel), color = SakuroColors.TextMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun BenchChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.4f),
            selectedLabelColor = SakuroColors.TextPrimary,
            labelColor = SakuroColors.TextMuted,
        ),
    )
}

@Composable
private fun BenchStage.label(): String = stringResource(
    when (this) {
        BenchStage.STARTING -> Res.string.bench_stage_starting
        BenchStage.PLAYING -> Res.string.bench_stage_playing
        BenchStage.CAPTURING -> Res.string.bench_stage_capturing
        BenchStage.PACKING -> Res.string.bench_stage_packing
    },
)

/** Rough wall-clock estimate: load + 8 s playback + three seek-captures per configuration. */
private fun estimateMinutes(configCount: Int): Int =
    ((configCount * SECONDS_PER_CONFIG) / 60).coerceAtLeast(1)

private const val SECONDS_PER_CONFIG = 20
