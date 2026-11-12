package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.navigation.PlaybackSettingsComponent
import com.rinwave.sakuro.ui.hint
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.settings_engine
import sakuro.composeapp.generated.resources.settings_subtitle_background
import sakuro.composeapp.generated.resources.settings_subtitle_background_hint
import sakuro.composeapp.generated.resources.settings_subtitle_delay
import sakuro.composeapp.generated.resources.settings_subtitle_delay_hint
import sakuro.composeapp.generated.resources.settings_subtitle_earlier
import sakuro.composeapp.generated.resources.settings_subtitle_font
import sakuro.composeapp.generated.resources.settings_subtitle_language
import sakuro.composeapp.generated.resources.settings_subtitle_language_hint
import sakuro.composeapp.generated.resources.settings_subtitle_later
import sakuro.composeapp.generated.resources.settings_subtitle_position
import sakuro.composeapp.generated.resources.settings_subtitle_reset
import sakuro.composeapp.generated.resources.settings_subtitles

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
    val subtitles by component.subtitlePreferences.collectAsState()
    SubtitleSettings(subtitles, component::setSubtitlePreferences)
}

@Composable
private fun SubtitleSettings(value: SubtitlePreferences, onChange: (SubtitlePreferences) -> Unit) {
    SettingsPanel(title = stringResource(Res.string.settings_subtitles), icon = Lucide.Film) {
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
