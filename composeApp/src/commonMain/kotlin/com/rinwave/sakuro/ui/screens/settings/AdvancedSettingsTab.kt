package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.rinwave.sakuro.navigation.AdvancedSettingsComponent
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.settings_adaptive_desc
import sakuro.composeapp.generated.resources.settings_adaptive_title
import sakuro.composeapp.generated.resources.settings_bench
import sakuro.composeapp.generated.resources.settings_bench_desc
import sakuro.composeapp.generated.resources.settings_debug_overlay
import sakuro.composeapp.generated.resources.settings_debug_overlay_desc
import sakuro.composeapp.generated.resources.settings_quality_mode

@Composable
internal fun AdvancedSettingsTab(component: AdvancedSettingsComponent) {
    val adaptiveEnabled by component.adaptiveEnabled.collectAsState()
    val debugOverlay by component.debugOverlay.collectAsState()

    SettingsPanel(title = stringResource(Res.string.settings_quality_mode), icon = Lucide.Settings) {
        SettingSwitchRow(
            title = stringResource(Res.string.settings_adaptive_title),
            subtitle = stringResource(Res.string.settings_adaptive_desc),
            checked = adaptiveEnabled,
            onCheckedChange = component::setAdaptiveEnabled,
        )
        PanelDivider()
        SettingSwitchRow(
            title = stringResource(Res.string.settings_debug_overlay),
            subtitle = stringResource(Res.string.settings_debug_overlay_desc),
            checked = debugOverlay,
            onCheckedChange = component::setDebugOverlay,
        )
    }

    SettingsPanel(title = stringResource(Res.string.settings_bench), icon = Lucide.Gauge) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = component.onOpenBench)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(Res.string.settings_bench),
                    color = SakuroColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.settings_bench_desc),
                    color = SakuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = SakuroColors.TextMuted)
        }
    }
}
