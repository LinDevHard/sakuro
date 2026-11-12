package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles
import com.rinwave.sakuro.navigation.SettingsMenuComponent
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.about_version
import sakuro.composeapp.generated.resources.settings_about
import sakuro.composeapp.generated.resources.settings_controls
import sakuro.composeapp.generated.resources.settings_section_about_desc
import sakuro.composeapp.generated.resources.settings_section_advanced_desc
import sakuro.composeapp.generated.resources.settings_section_controls_desc
import sakuro.composeapp.generated.resources.settings_section_playback_desc
import sakuro.composeapp.generated.resources.settings_section_upscale_desc
import sakuro.composeapp.generated.resources.settings_tab_advanced
import sakuro.composeapp.generated.resources.settings_tab_playback
import sakuro.composeapp.generated.resources.settings_tab_upscale
import sakuro.composeapp.generated.resources.settings_title

@Composable
internal fun SettingsMenuScreen(component: SettingsMenuComponent) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SakuroColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsTopBar(
            title = stringResource(Res.string.settings_title),
            onBack = component.onBack,
            trailing = { TopBarPill(stringResource(Res.string.about_version, component.version)) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuRow(
                icon = Lucide.Film,
                accent = SakuroColors.AccentSakura,
                title = stringResource(Res.string.settings_tab_playback),
                subtitle = stringResource(Res.string.settings_section_playback_desc),
                onClick = component.onOpenPlayback,
            )
            MenuRow(
                icon = Lucide.Sparkles,
                accent = SakuroColors.GlowMagenta,
                title = stringResource(Res.string.settings_tab_upscale),
                subtitle = stringResource(Res.string.settings_section_upscale_desc),
                onClick = component.onOpenUpscale,
            )
            MenuRow(
                icon = Lucide.Activity,
                accent = SakuroColors.AccentLavender,
                title = stringResource(Res.string.settings_controls),
                subtitle = stringResource(Res.string.settings_section_controls_desc),
                onClick = component.onOpenControls,
            )
            MenuRow(
                icon = Lucide.Settings,
                accent = SakuroColors.AccentSakura,
                title = stringResource(Res.string.settings_tab_advanced),
                subtitle = stringResource(Res.string.settings_section_advanced_desc),
                onClick = component.onOpenAdvanced,
            )
            MenuRow(
                icon = Lucide.Info,
                accent = SakuroColors.AccentLavender,
                title = stringResource(Res.string.settings_about),
                subtitle = stringResource(Res.string.settings_section_about_desc),
                onClick = component.onOpenAbout,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.15f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(11.dp).size(20.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = SakuroColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
