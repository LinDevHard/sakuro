package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.rinwave.sakuro.navigation.AdvancedSettingsComponent
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.settings_adaptive_desc
import sakuro.composeapp.generated.resources.settings_adaptive_title
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
}
