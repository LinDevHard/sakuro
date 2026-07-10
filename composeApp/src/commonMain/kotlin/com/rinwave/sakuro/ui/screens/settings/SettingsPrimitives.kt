package com.rinwave.sakuro.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.ui.theme.SakuroColors

/**
 * A titled card that groups related settings. The header shows an accented icon,
 * a title and an optional one-line description; [content] renders the rows.
 */
@Composable
internal fun SettingsPanel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = SakuroColors.AccentSakura,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = SakuroColors.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SakuroColors.Twilight.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = accent.copy(alpha = 0.15f), shape = CircleShape) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(18.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        title,
                        color = SakuroColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = SakuroColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            content()
        }
    }
}

/** A hairline divider inset to align with row content. */
@Composable
internal fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(1.dp)
            .background(SakuroColors.Twilight.copy(alpha = 0.35f)),
    )
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    inset: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = if (inset) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(subtitle, color = SakuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SakuroColors.TextPrimary,
                checkedTrackColor = SakuroColors.GlowMagenta,
                checkedBorderColor = SakuroColors.GlowMagenta,
                uncheckedTrackColor = SakuroColors.SurfaceElevated,
                uncheckedBorderColor = SakuroColors.Twilight,
            ),
        )
    }
}

@Composable
internal fun SettingSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (enabled) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = SakuroColors.AccentSakura.copy(alpha = if (enabled) 0.16f else 0.06f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    valueText,
                    color = if (enabled) SakuroColors.AccentSakura else SakuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SakuroColors.AccentSakura,
                activeTrackColor = SakuroColors.GlowMagenta,
                inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
            ),
        )
    }
}
