package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Github
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Zap
import com.rinwave.sakuro.navigation.AboutSettingsComponent
import com.rinwave.sakuro.ui.theme.SakuroColors
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.about_credits
import sakuro.composeapp.generated.resources.about_credits_text
import sakuro.composeapp.generated.resources.about_link_donate
import sakuro.composeapp.generated.resources.about_link_donate_desc
import sakuro.composeapp.generated.resources.about_link_github
import sakuro.composeapp.generated.resources.about_link_github_desc
import sakuro.composeapp.generated.resources.about_link_issues
import sakuro.composeapp.generated.resources.about_link_issues_desc
import sakuro.composeapp.generated.resources.about_link_license
import sakuro.composeapp.generated.resources.about_link_license_desc
import sakuro.composeapp.generated.resources.about_link_releases
import sakuro.composeapp.generated.resources.about_link_releases_desc
import sakuro.composeapp.generated.resources.about_links
import sakuro.composeapp.generated.resources.about_made_by
import sakuro.composeapp.generated.resources.about_open_source
import sakuro.composeapp.generated.resources.about_tagline
import sakuro.composeapp.generated.resources.about_version

@Composable
internal fun AboutSettingsTab(component: AboutSettingsComponent) {
    val uriHandler = LocalUriHandler.current

    AboutHero(version = component.version)

    SettingsPanel(
        title = stringResource(Res.string.about_links),
        icon = Lucide.ExternalLink,
        accent = SakuroColors.AccentLavender,
    ) {
        LinkRow(
            icon = Lucide.Github,
            title = stringResource(Res.string.about_link_github),
            subtitle = stringResource(Res.string.about_link_github_desc),
            onClick = { uriHandler.openUri(component.githubUrl) },
        )
        PanelDivider()
        LinkRow(
            icon = Lucide.Star,
            title = stringResource(Res.string.about_link_releases),
            subtitle = stringResource(Res.string.about_link_releases_desc),
            onClick = { uriHandler.openUri(component.releasesUrl) },
        )
        PanelDivider()
        LinkRow(
            icon = Lucide.Zap,
            title = stringResource(Res.string.about_link_issues),
            subtitle = stringResource(Res.string.about_link_issues_desc),
            onClick = { uriHandler.openUri(component.issuesUrl) },
        )
        PanelDivider()
        LinkRow(
            icon = Lucide.Coffee,
            title = stringResource(Res.string.about_link_donate),
            subtitle = stringResource(Res.string.about_link_donate_desc),
            accent = SakuroColors.GlowMagenta,
            onClick = { uriHandler.openUri(component.donateUrl) },
        )
        PanelDivider()
        LinkRow(
            icon = Lucide.Scale,
            title = stringResource(Res.string.about_link_license),
            subtitle = stringResource(Res.string.about_link_license_desc),
            onClick = { uriHandler.openUri(component.licenseUrl) },
        )
    }

    SettingsPanel(
        title = stringResource(Res.string.about_credits),
        icon = Lucide.Heart,
        accent = SakuroColors.GlowMagenta,
    ) {
        Text(
            stringResource(Res.string.about_credits_text),
            color = SakuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AboutHero(version: String) {
    val wordmarkBrush = Brush.horizontalGradient(
        listOf(SakuroColors.AccentSakura, SakuroColors.AccentLavender),
    )
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(SakuroColors.GlowMagenta, SakuroColors.AccentLavender),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Sparkles,
                    contentDescription = null,
                    tint = SakuroColors.TextPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                "Sakuro",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = wordmarkBrush,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                stringResource(Res.string.about_tagline),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroPill(text = stringResource(Res.string.about_version, version), accent = SakuroColors.AccentSakura)
                HeroPill(text = stringResource(Res.string.about_open_source), accent = SakuroColors.AccentLavender)
            }
            Text(
                stringResource(Res.string.about_made_by),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun HeroPill(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    ) {
        Text(
            text,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: Color = SakuroColors.AccentSakura,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = accent.copy(alpha = 0.14f), shape = CircleShape) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(9.dp).size(18.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            Lucide.ExternalLink,
            contentDescription = null,
            tint = SakuroColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
