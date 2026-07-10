package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.navigation.PlaybackSettingsComponent
import com.rinwave.sakuro.ui.hint
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.settings_engine

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
