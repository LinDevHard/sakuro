package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.settings.SakuroSettings
import com.rinwave.sakuro.navigation.ControlsSettingsComponent
import com.rinwave.sakuro.ui.formatMultiplier
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.settings_controls
import sakuro.composeapp.generated.resources.settings_gesture_brightness
import sakuro.composeapp.generated.resources.settings_gesture_brightness_desc
import sakuro.composeapp.generated.resources.settings_gesture_seek
import sakuro.composeapp.generated.resources.settings_gesture_seek_desc
import sakuro.composeapp.generated.resources.settings_gesture_volume
import sakuro.composeapp.generated.resources.settings_gesture_volume_desc
import sakuro.composeapp.generated.resources.settings_gesture_zoom
import sakuro.composeapp.generated.resources.settings_gesture_zoom_desc
import sakuro.composeapp.generated.resources.settings_player_gestures
import sakuro.composeapp.generated.resources.settings_player_gestures_desc
import sakuro.composeapp.generated.resources.settings_swipe_sensitivity

// 0.5×..2× with a 0.25 step gives 5 intermediate slider ticks.
private const val SENSITIVITY_STEPS = 5

@Composable
internal fun ControlsSettingsTab(component: ControlsSettingsComponent) {
    val gesturesEnabled by component.gesturesEnabled.collectAsState()
    val gestureBrightness by component.gestureBrightness.collectAsState()
    val gestureVolume by component.gestureVolume.collectAsState()
    val gestureSeek by component.gestureSeek.collectAsState()
    val gestureZoom by component.gestureZoom.collectAsState()
    val gestureSensitivity by component.gestureSensitivity.collectAsState()

    SettingsPanel(title = stringResource(Res.string.settings_controls), icon = Lucide.Activity) {
        SettingSwitchRow(
            title = stringResource(Res.string.settings_player_gestures),
            subtitle = stringResource(Res.string.settings_player_gestures_desc),
            checked = gesturesEnabled,
            onCheckedChange = component::setGesturesEnabled,
        )
        PanelDivider()
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_brightness),
            subtitle = stringResource(Res.string.settings_gesture_brightness_desc),
            checked = gestureBrightness,
            onCheckedChange = component::setGestureBrightness,
            enabled = gesturesEnabled,
            inset = true,
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_volume),
            subtitle = stringResource(Res.string.settings_gesture_volume_desc),
            checked = gestureVolume,
            onCheckedChange = component::setGestureVolume,
            enabled = gesturesEnabled,
            inset = true,
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_seek),
            subtitle = stringResource(Res.string.settings_gesture_seek_desc),
            checked = gestureSeek,
            onCheckedChange = component::setGestureSeek,
            enabled = gesturesEnabled,
            inset = true,
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_zoom),
            subtitle = stringResource(Res.string.settings_gesture_zoom_desc),
            checked = gestureZoom,
            onCheckedChange = component::setGestureZoom,
            enabled = gesturesEnabled,
            inset = true,
        )
        PanelDivider()
        SettingSliderRow(
            title = stringResource(Res.string.settings_swipe_sensitivity),
            valueText = "${formatMultiplier(gestureSensitivity)}×",
            value = gestureSensitivity,
            valueRange = SakuroSettings.SENSITIVITY_MIN..SakuroSettings.SENSITIVITY_MAX,
            steps = SENSITIVITY_STEPS,
            enabled = gesturesEnabled,
            onValueChange = component::setGestureSensitivity,
        )
    }
}
