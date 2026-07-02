package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.displayName
import com.rinwave.sakuro.navigation.SettingsComponent
import com.rinwave.sakuro.ui.theme.SakuroColors

@Composable
fun SettingsScreen(component: SettingsComponent) {
    val engineType by component.engineType.collectAsState()
    val presetId by component.presetId.collectAsState()
    val debugOverlay by component.debugOverlay.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = component.onBack) {
                Icon(Lucide.ChevronLeft, "Назад", tint = SakuroColors.TextPrimary)
            }
            Text("Настройки", style = MaterialTheme.typography.titleLarge, color = SakuroColors.TextPrimary)
        }

        SectionTitle("Движок воспроизведения")
        component.availableEngines.forEach { type ->
            EngineRow(
                type = type,
                selected = type == engineType,
                enabled = true,
                onClick = { component.selectEngine(type) },
            )
        }
        if (EngineType.MPV !in component.availableEngines) {
            EngineRow(type = EngineType.MPV, selected = false, enabled = false, onClick = {})
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Пресет апскейла по умолчанию")
        component.presets.forEach { preset ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { component.selectPreset(preset.id) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preset.id == presetId,
                    onClick = { component.selectPreset(preset.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = SakuroColors.AccentSakura),
                )
                Column(Modifier.padding(start = 4.dp)) {
                    Text(preset.name, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(preset.description, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("Отладка")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { component.setDebugOverlay(!debugOverlay) }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Debug-оверлей", color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text("«Stats for nerds» поверх плеера", color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = debugOverlay,
                onCheckedChange = component::setDebugOverlay,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SakuroColors.AccentSakura,
                    checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
                ),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SakuroColors.Twilight.copy(alpha = 0.4f))

        SectionTitle("О приложении")
        Text(
            "Sakuro 0.1.0 — видеоплеер с реалтайм-апскейлом.\nOpen source (GPLv3), by Rinwave.",
            color = SakuroColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = SakuroColors.AccentLavender,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun EngineRow(type: EngineType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = SakuroColors.AccentSakura),
        )
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                type.displayName,
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            val hint = when (type) {
                EngineType.MEDIA3 -> "Нативный Android-движок, апскейл через GlEffect-цепочку"
                EngineType.MPV -> "Anime4K/ArtCNN нативно — скоро (Фаза 1, NDK-сборка)"
                EngineType.FAKE -> "Мок для отладки интерфейса без воспроизведения"
            }
            Text(hint, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
