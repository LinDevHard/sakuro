package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.navigation.SettingsComponent
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_back
import sakuro.composeapp.generated.resources.settings_about
import sakuro.composeapp.generated.resources.settings_controls
import sakuro.composeapp.generated.resources.settings_tab_advanced
import sakuro.composeapp.generated.resources.settings_tab_playback
import sakuro.composeapp.generated.resources.settings_tab_upscale

@Composable
fun SettingsScreen(component: SettingsComponent) {
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

/** A section screen: a back-titled top bar over the section's scrolling content. */
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SakuroColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsTopBar(title = title, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Shared top bar: a back chevron, the title, and an optional trailing slot. */
@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ChevronLeft, stringResource(Res.string.action_back), tint = SakuroColors.TextPrimary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SakuroColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
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
