package com.rinwave.sakuro.feature.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronsRight
import com.composables.icons.lucide.Lucide
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.core.player.SkipKind
import com.rinwave.sakuro.feature.player.resources.Res
import com.rinwave.sakuro.feature.player.resources.player_skip_85
import com.rinwave.sakuro.feature.player.resources.player_skip_85_hint
import com.rinwave.sakuro.feature.player.resources.player_skip_ending
import com.rinwave.sakuro.feature.player.resources.player_skip_opening
import org.jetbrains.compose.resources.stringResource

/** "Skip opening" / "Skip ending": stays on screen for the whole segment, controls or not. */
@Composable
internal fun SkipSegmentButton(kind: SkipKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (kind) {
        SkipKind.OPENING -> stringResource(Res.string.player_skip_opening)
        SkipKind.ENDING -> stringResource(Res.string.player_skip_ending)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SakuroColors.SurfaceElevated.copy(alpha = 0.92f),
        contentColor = SakuroColors.TextPrimary,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Icon(Lucide.ChevronsRight, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
        }
    }
}

/** The fallback for files without chapters: jump the length of a typical opening. */
@Composable
internal fun SkipOpeningJumpChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(Res.string.player_skip_85_hint)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = SakuroColors.Surface.copy(alpha = 0.6f),
        contentColor = SakuroColors.TextPrimary,
        modifier = modifier.semantics { contentDescription = hint },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.player_skip_85), style = MaterialTheme.typography.labelMedium)
            Icon(Lucide.ChevronsRight, null, modifier = Modifier.size(14.dp))
        }
    }
}
