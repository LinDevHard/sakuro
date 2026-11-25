package com.rinwave.sakuro.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.rinwave.sakuro.core.designsystem.PanelDivider
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.designsystem.SettingSliderRow
import com.rinwave.sakuro.core.designsystem.SettingSwitchRow
import com.rinwave.sakuro.core.designsystem.SettingsPanel
import com.rinwave.sakuro.core.designsystem.formatSize
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.FrameRateMatching
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.player.MediaCache
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.core.ui.hint
import com.rinwave.sakuro.core.ui.label
import com.rinwave.sakuro.feature.settings.resources.Res
import com.rinwave.sakuro.feature.settings.resources.settings_ass_renderer
import com.rinwave.sakuro.feature.settings.resources.settings_ass_renderer_hint
import com.rinwave.sakuro.feature.settings.resources.settings_audio
import com.rinwave.sakuro.feature.settings.resources.settings_audio_delay
import com.rinwave.sakuro.feature.settings.resources.settings_audio_delay_hint
import com.rinwave.sakuro.feature.settings.resources.settings_audio_earlier
import com.rinwave.sakuro.feature.settings.resources.settings_audio_later
import com.rinwave.sakuro.feature.settings.resources.settings_background_playback
import com.rinwave.sakuro.feature.settings.resources.settings_background_playback_hint
import com.rinwave.sakuro.feature.settings.resources.settings_cache
import com.rinwave.sakuro.feature.settings.resources.settings_cache_clear
import com.rinwave.sakuro.feature.settings.resources.settings_cache_hint
import com.rinwave.sakuro.feature.settings.resources.settings_cache_off
import com.rinwave.sakuro.feature.settings.resources.settings_cache_used
import com.rinwave.sakuro.feature.settings.resources.settings_display
import com.rinwave.sakuro.feature.settings.resources.settings_engine
import com.rinwave.sakuro.feature.settings.resources.settings_frame_rate
import com.rinwave.sakuro.feature.settings.resources.settings_frame_rate_always
import com.rinwave.sakuro.feature.settings.resources.settings_frame_rate_hint
import com.rinwave.sakuro.feature.settings.resources.settings_frame_rate_off
import com.rinwave.sakuro.feature.settings.resources.settings_frame_rate_seamless
import com.rinwave.sakuro.feature.settings.resources.settings_streaming
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_background
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_background_hint
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_delay
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_delay_hint
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_earlier
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_font
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_language
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_language_hint
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_later
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_position
import com.rinwave.sakuro.feature.settings.resources.settings_subtitle_reset
import com.rinwave.sakuro.feature.settings.resources.settings_subtitles
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaybackSettingsTab(component: PlaybackSettingsComponent) {
    val engineType by component.engineType.collectAsState()

    SettingsPanel(title = stringResource(Res.string.settings_engine), icon = Lucide.Film) {
        component.availableEngines.forEachIndexed { index, type ->
            if (index > 0) PanelDivider()
            EngineRow(
                type = type,
                selected = type == engineType,
                enabled = true,
                onClick = { component.selectEngine(type) },
            )
        }
        if (EngineType.MPV !in component.availableEngines) {
            PanelDivider()
            EngineRow(type = EngineType.MPV, selected = false, enabled = false, onClick = {})
        }
    }
    DisplaySettings(component)
    AudioSettings(component)
    StreamingSettings(component)
    val subtitles by component.subtitlePreferences.collectAsState()
    val assRenderer by component.assRenderer.collectAsState()
    SubtitleSettings(subtitles, component::setSubtitlePreferences, assRenderer, component::setAssRenderer)
}

/** Display refresh rate following the video's frame rate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplaySettings(component: PlaybackSettingsComponent) {
    val matching by component.frameRateMatching.collectAsState()
    SettingsPanel(title = stringResource(Res.string.settings_display), icon = Lucide.Monitor) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.settings_frame_rate), color = SakuroColors.TextPrimary)
            Text(
                stringResource(Res.string.settings_frame_rate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SakuroColors.TextMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FrameRateMatching.entries.forEach { option ->
                    FilterChip(
                        selected = option == matching,
                        onClick = { component.setFrameRateMatching(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        FrameRateMatching.OFF -> Res.string.settings_frame_rate_off
                                        FrameRateMatching.SEAMLESS -> Res.string.settings_frame_rate_seamless
                                        FrameRateMatching.ALWAYS -> Res.string.settings_frame_rate_always
                                    },
                                ),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.35f),
                            selectedLabelColor = SakuroColors.AccentSakura,
                        ),
                    )
                }
            }
        }
    }
}

/** Audio offset against the picture, for headphones with misreported latency. */
@Composable
private fun AudioSettings(component: PlaybackSettingsComponent) {
    val delayMs by component.audioDelayMs.collectAsState()
    SettingsPanel(title = stringResource(Res.string.settings_audio), icon = Lucide.Headphones) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val signed = when {
                delayMs > 0 -> "+$delayMs"
                delayMs < 0 -> "\u2212${-delayMs}"
                else -> "0"
            }
            Text(stringResource(Res.string.settings_audio_delay, signed), color = SakuroColors.TextPrimary)
            Text(
                stringResource(Res.string.settings_audio_delay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SakuroColors.TextMuted,
            )
            Row {
                TextButton(
                    onClick = { component.setAudioDelayMs(delayMs - AUDIO_DELAY_STEP_MS) },
                    enabled = delayMs > -SakuroSettings.MAX_AUDIO_DELAY_MS,
                ) { Text(stringResource(Res.string.settings_audio_earlier)) }
                TextButton(onClick = { component.setAudioDelayMs(0) }, enabled = delayMs != 0) {
                    Text(stringResource(Res.string.settings_subtitle_reset))
                }
                TextButton(
                    onClick = { component.setAudioDelayMs(delayMs + AUDIO_DELAY_STEP_MS) },
                    enabled = delayMs < SakuroSettings.MAX_AUDIO_DELAY_MS,
                ) { Text(stringResource(Res.string.settings_audio_later)) }
            }
        }
    }
}

private const val AUDIO_DELAY_STEP_MS = 50

/** Background playback and the network-stream cache. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamingSettings(component: PlaybackSettingsComponent) {
    val background by component.backgroundPlayback.collectAsState()
    val limitMb by component.cacheLimitMb.collectAsState()
    val usedBytes by component.cacheUsedBytes.collectAsState()
    SettingsPanel(title = stringResource(Res.string.settings_streaming), icon = Lucide.HardDrive) {
        SettingSwitchRow(
            title = stringResource(Res.string.settings_background_playback),
            subtitle = stringResource(Res.string.settings_background_playback_hint),
            checked = background,
            onCheckedChange = component::setBackgroundPlayback,
        )
        PanelDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.settings_cache), color = SakuroColors.TextPrimary)
            Text(
                stringResource(Res.string.settings_cache_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SakuroColors.TextMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                MediaCache.LIMIT_CHOICES_MB.forEach { choice ->
                    FilterChip(
                        selected = choice == limitMb,
                        onClick = { component.setCacheLimitMb(choice) },
                        label = {
                            Text(
                                if (choice == 0) {
                                    stringResource(Res.string.settings_cache_off)
                                } else {
                                    formatSize(choice * MediaCache.MB)
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SakuroColors.GlowMagenta.copy(alpha = 0.35f),
                            selectedLabelColor = SakuroColors.AccentSakura,
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        Res.string.settings_cache_used,
                        if (usedBytes > 0) formatSize(usedBytes) else "0 MB",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SakuroColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = component::clearCache, enabled = usedBytes > 0) {
                    Text(stringResource(Res.string.settings_cache_clear))
                }
            }
        }
    }
}

@Composable
private fun SubtitleSettings(
    value: SubtitlePreferences,
    onChange: (SubtitlePreferences) -> Unit,
    assRenderer: Boolean,
    onAssRenderer: (Boolean) -> Unit,
) {
    SettingsPanel(title = stringResource(Res.string.settings_subtitles), icon = Lucide.Film) {
        SettingSwitchRow(
            title = stringResource(Res.string.settings_ass_renderer),
            subtitle = stringResource(Res.string.settings_ass_renderer_hint),
            checked = assRenderer,
            onCheckedChange = onAssRenderer,
        )
        SettingSliderRow(
            title = stringResource(Res.string.settings_subtitle_font),
            valueText = "${value.fontSizeSp.toInt()} sp",
            value = value.fontSizeSp, valueRange = 12f..48f, steps = 35, enabled = true,
            onValueChange = { onChange(value.copy(fontSizeSp = it)) },
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_subtitle_background),
            subtitle = stringResource(Res.string.settings_subtitle_background_hint),
            checked = value.backgroundEnabled,
            onCheckedChange = { onChange(value.copy(backgroundEnabled = it)) },
        )
        SettingSliderRow(
            title = stringResource(Res.string.settings_subtitle_position),
            valueText = "${(value.bottomPaddingFraction * 100).toInt()}%",
            value = value.bottomPaddingFraction, valueRange = 0f..0.4f, steps = 19, enabled = true,
            onValueChange = { onChange(value.copy(bottomPaddingFraction = it)) },
        )
        SubtitleDelaySettings(value, onChange)
        OutlinedTextField(
            value = value.preferredLanguage,
            onValueChange = { onChange(value.copy(preferredLanguage = it)) },
            label = { Text(stringResource(Res.string.settings_subtitle_language)) },
            supportingText = { Text(stringResource(Res.string.settings_subtitle_language_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SubtitleDelaySettings(value: SubtitlePreferences, onChange: (SubtitlePreferences) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(Res.string.settings_subtitle_delay, value.delayMs.toInt()),
            color = SakuroColors.TextPrimary,
        )
        Text(
            stringResource(Res.string.settings_subtitle_delay_hint),
            style = MaterialTheme.typography.bodySmall, color = SakuroColors.TextMuted,
        )
        Row {
            TextButton(
                onClick = { onChange(value.copy(delayMs = value.delayMs - 100)) },
                enabled = value.delayMs > -30_000,
            ) { Text(stringResource(Res.string.settings_subtitle_earlier)) }
            TextButton(onClick = { onChange(value.copy(delayMs = 0)) }) {
                Text(stringResource(Res.string.settings_subtitle_reset))
            }
            TextButton(
                onClick = { onChange(value.copy(delayMs = value.delayMs + 100)) },
                enabled = value.delayMs < 30_000,
            ) { Text(stringResource(Res.string.settings_subtitle_later)) }
        }
    }
}

@Composable
private fun EngineRow(type: EngineType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = SakuroColors.AccentSakura,
                unselectedColor = SakuroColors.TextMuted,
            ),
        )
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                type.label(),
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(type.hint(), color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
