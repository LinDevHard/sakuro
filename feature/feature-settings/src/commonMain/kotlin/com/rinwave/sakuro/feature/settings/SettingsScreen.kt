package com.rinwave.sakuro.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.designsystem.SettingsDetailScaffold
import com.rinwave.sakuro.feature.settings.resources.Res
import com.rinwave.sakuro.feature.settings.resources.settings_about
import com.rinwave.sakuro.feature.settings.resources.settings_controls
import com.rinwave.sakuro.feature.settings.resources.settings_tab_advanced
import com.rinwave.sakuro.feature.settings.resources.settings_tab_playback
import com.rinwave.sakuro.feature.settings.resources.settings_tab_upscale
import org.jetbrains.compose.resources.stringResource

@Composable
public fun SettingsScreen(component: SettingsComponent) {
    Children(
        stack = component.stack,
        animation = stackAnimation(slide()),
    ) { child ->
        when (val instance = child.instance) {
            is SettingsComponent.Child.Menu -> SettingsMenuScreen(instance.component)

            is SettingsComponent.Child.Playback ->
                SettingsDetailScaffold(stringResource(Res.string.settings_tab_playback), component::pop) {
                    PlaybackSettingsTab(instance.component)
                }

            is SettingsComponent.Child.Upscale ->
                SettingsDetailScaffold(stringResource(Res.string.settings_tab_upscale), component::pop) {
                    UpscaleSettingsTab(instance.component)
                }

            is SettingsComponent.Child.Controls ->
                SettingsDetailScaffold(stringResource(Res.string.settings_controls), component::pop) {
                    ControlsSettingsTab(instance.component)
                }

            is SettingsComponent.Child.Advanced ->
                SettingsDetailScaffold(stringResource(Res.string.settings_tab_advanced), component::pop) {
                    AdvancedSettingsTab(instance.component)
                }

            is SettingsComponent.Child.About ->
                SettingsDetailScaffold(stringResource(Res.string.settings_about), component::pop) {
                    AboutSettingsTab(instance.component)
                }
        }
    }
}

/** A rounded pill used in top bars (e.g. the app version). */
@Composable
internal fun TopBarPill(text: String) {
    Surface(color = SakuroColors.SurfaceElevated, shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            color = SakuroColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
