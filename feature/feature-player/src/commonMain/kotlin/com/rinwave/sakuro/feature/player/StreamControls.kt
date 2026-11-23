package com.rinwave.sakuro.feature.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.designsystem.formatTime
import com.rinwave.sakuro.core.player.VideoQuality
import com.rinwave.sakuro.core.player.VideoQualityLabels
import com.rinwave.sakuro.feature.player.resources.Res
import com.rinwave.sakuro.feature.player.resources.player_live
import com.rinwave.sakuro.feature.player.resources.player_live_go
import com.rinwave.sakuro.feature.player.resources.player_quality
import com.rinwave.sakuro.feature.player.resources.player_quality_auto
import com.rinwave.sakuro.feature.player.resources.player_quality_auto_hint
import com.rinwave.sakuro.feature.player.resources.player_quality_auto_playing
import org.jetbrains.compose.resources.stringResource

/** Within this distance of the live edge the badge reads as "live" (red, pulsing). */
private const val LIVE_EDGE_TOLERANCE_MS = 3_000L
private val LiveRed = Color(0xFFFF3B5C)

/**
 * "● LIVE" pill: red and pulsing at the live edge; muted with "−0:42" when behind, and then a
 * tap jumps back to the edge.
 */
@Composable
internal fun LiveBadge(behindMs: Long, onGoLive: () -> Unit, modifier: Modifier = Modifier) {
    val atEdge = behindMs <= LIVE_EDGE_TOLERANCE_MS
    val goLive = stringResource(Res.string.player_live_go)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onGoLive,
            enabled = !atEdge,
            shape = RoundedCornerShape(50),
            color = if (atEdge) LiveRed.copy(alpha = 0.18f) else SakuroColors.Surface.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (atEdge) LiveRed.copy(alpha = 0.7f) else SakuroColors.TextMuted.copy(alpha = 0.4f),
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiveDot(pulsing = atEdge)
                Text(
                    text = if (atEdge) stringResource(Res.string.player_live) else goLive,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = if (atEdge) SakuroColors.TextPrimary else SakuroColors.TextMuted,
                )
            }
        }
        if (!atEdge) {
            Text(
                text = "−" + formatTime(behindMs),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = SakuroColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun LiveDot(pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "live")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "liveDot",
        )
        value
    } else {
        1f
    }
    Box(
        Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(if (pulsing) LiveRed else SakuroColors.TextMuted, CircleShape),
    )
}

/**
 * The quality pill in the top bar and its popup: "Auto · 720p", then 4K / 1080p / 720p …
 * with the bitrate as a quiet second line so equal resolutions stay distinguishable.
 */
@Composable
internal fun QualityButton(
    qualities: List<VideoQuality>,
    selectedId: String?,
    playingId: String?,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = qualities.firstOrNull { it.id == selectedId }
    val playing = qualities.firstOrNull { it.id == playingId }
    val autoLabel = stringResource(Res.string.player_quality_auto)
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = SakuroColors.Surface.copy(alpha = 0.6f),
            contentColor = if (selected != null) SakuroColors.AccentSakura else SakuroColors.TextMuted,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.SlidersHorizontal, stringResource(Res.string.player_quality), Modifier.size(14.dp))
                Text(
                    text = selected?.label ?: playing?.let { "$autoLabel · ${it.label}" } ?: autoLabel,
                    fontSize = 12.sp,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = SakuroColors.SurfaceElevated,
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            Text(
                stringResource(Res.string.player_quality),
                style = MaterialTheme.typography.labelLarge,
                color = SakuroColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            QualityItem(
                title = autoLabel,
                subtitle = playing?.let { stringResource(Res.string.player_quality_auto_playing, it.label) }
                    ?: stringResource(Res.string.player_quality_auto_hint),
                checked = selectedId == null,
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            qualities.forEach { quality ->
                QualityItem(
                    title = quality.label,
                    subtitle = listOfNotNull(
                        "${quality.width}×${quality.height}",
                        VideoQualityLabels.bitrateText(quality.bitrate),
                    ).joinToString(" · "),
                    checked = quality.id == selectedId,
                    badge = qualityBadge(quality),
                    onClick = {
                        open = false
                        onSelect(quality.id)
                    },
                )
            }
        }
    }
}

private fun qualityBadge(quality: VideoQuality): String? {
    val nominal = VideoQualityLabels.nominalHeight(quality.width, quality.height)
    return when {
        nominal >= UHD_HEIGHT -> "UHD"
        nominal >= FULL_HD_HEIGHT -> "HD"
        else -> null
    }
}

private const val UHD_HEIGHT = 2160
private const val FULL_HD_HEIGHT = 1080

@Composable
private fun QualityItem(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit, badge: String? = null) {
    DropdownMenuItem(
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (checked) SakuroColors.AccentSakura else SakuroColors.TextPrimary,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SakuroColors.AccentSakura,
                            modifier = Modifier
                                .border(1.dp, SakuroColors.AccentSakura.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SakuroColors.TextMuted)
            }
        },
        trailingIcon = {
            if (checked) Icon(Lucide.Check, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
    )
}
