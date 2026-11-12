package com.rinwave.sakuro.feature.bench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.designsystem.SettingsDetailScaffold
import com.rinwave.sakuro.core.designsystem.SettingsPanel
import com.rinwave.sakuro.core.ui.displayName
import com.rinwave.sakuro.core.ui.label
import com.rinwave.sakuro.core.ui.rememberVideoFilePicker
import com.rinwave.sakuro.feature.bench.resources.Res
import com.rinwave.sakuro.feature.bench.resources.bench_cancel
import com.rinwave.sakuro.feature.bench.resources.bench_done
import com.rinwave.sakuro.feature.bench.resources.bench_done_desc
import com.rinwave.sakuro.feature.bench.resources.bench_engines
import com.rinwave.sakuro.feature.bench.resources.bench_estimate
import com.rinwave.sakuro.feature.bench.resources.bench_failed
import com.rinwave.sakuro.feature.bench.resources.bench_pick_video
import com.rinwave.sakuro.feature.bench.resources.bench_presets
import com.rinwave.sakuro.feature.bench.resources.bench_presets_hint
import com.rinwave.sakuro.feature.bench.resources.bench_progress
import com.rinwave.sakuro.feature.bench.resources.bench_run
import com.rinwave.sakuro.feature.bench.resources.bench_shaders
import com.rinwave.sakuro.feature.bench.resources.bench_shaders_all
import com.rinwave.sakuro.feature.bench.resources.bench_shaders_hint
import com.rinwave.sakuro.feature.bench.resources.bench_shaders_imported
import com.rinwave.sakuro.feature.bench.resources.bench_share
import com.rinwave.sakuro.feature.bench.resources.bench_stage_capturing
import com.rinwave.sakuro.feature.bench.resources.bench_stage_packing
import com.rinwave.sakuro.feature.bench.resources.bench_stage_playing
import com.rinwave.sakuro.feature.bench.resources.bench_stage_starting
import com.rinwave.sakuro.feature.bench.resources.bench_title
import com.rinwave.sakuro.feature.bench.resources.bench_unsupported
import com.rinwave.sakuro.feature.bench.resources.bench_video
import org.jetbrains.compose.resources.stringResource

/**
 * Benchmark setup and progress: pick a video, choose the engine × preset
 * matrix, run it, then share the resulting bundle with `tools/sakuro-bench`.
 */
@Composable
public fun BenchScreen(component: BenchComponent) {
    SettingsDetailScaffold(stringResource(Res.string.bench_title), component.onBack) {
        BenchContent(component)
    }
}

@Composable
private fun BenchContent(component: BenchComponent) {
    val runner = rememberBenchRunner(component.engineRegistry, component.gpuInfo)
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
    val shaders by component.shaders.collectAsState()
    val selectedShaders by component.selectedShaders.collectAsState()
    val allShadersSelected by component.allShadersSelected.collectAsState()
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

    SettingsPanel(
        title = stringResource(Res.string.bench_shaders),
        icon = Lucide.Layers,
        subtitle = stringResource(Res.string.bench_shaders_hint),
    ) {
        ChipFlow(Modifier.padding(horizontal = 16.dp)) {
            BenchChip(
                label = stringResource(Res.string.bench_shaders_all),
                selected = allShadersSelected,
                enabled = !running && shaders.isNotEmpty(),
                onClick = component::toggleAllShaders,
            )
            shaders.forEach { option ->
                val bundled = option.bundled
                BenchChip(
                    label = bundled?.displayName ?: option.fileName,
                    detail = bundled?.let { "${it.role.label()} · ${it.cost.label()}" }
                        ?: stringResource(Res.string.bench_shaders_imported),
                    selected = option.fileName in selectedShaders,
                    enabled = !running,
                    onClick = { component.toggleShader(option.fileName) },
                )
            }
        }
    }

    val configCount = selectedEngines.size * (selectedPresets.size + selectedShaders.size)
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

/** A chip; with [detail] it grows a muted second line (shader role and cost). */
@Composable
private fun BenchChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = if (detail == null) Modifier else Modifier.height(52.dp),
        label = {
            if (detail == null) {
                Text(label)
            } else {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = SakuroColors.TextMuted)
                }
            }
        },
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
