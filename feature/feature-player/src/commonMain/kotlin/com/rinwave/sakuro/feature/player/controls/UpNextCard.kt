package com.rinwave.sakuro.feature.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.feature.player.resources.Res
import com.rinwave.sakuro.feature.player.resources.player_up_next
import com.rinwave.sakuro.feature.player.resources.player_up_next_cancel
import com.rinwave.sakuro.feature.player.resources.player_up_next_in
import com.rinwave.sakuro.feature.player.resources.player_up_next_play
import org.jetbrains.compose.resources.stringResource

/** End-of-video card: the next file in the folder, with the autoplay countdown when it runs. */
@Composable
internal fun UpNextCard(
    title: String,
    secondsLeft: Int?,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SakuroColors.SurfaceElevated.copy(alpha = 0.94f),
        contentColor = SakuroColors.TextPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (secondsLeft != null) {
                    stringResource(Res.string.player_up_next_in, secondsLeft)
                } else {
                    stringResource(Res.string.player_up_next)
                },
                style = MaterialTheme.typography.labelMedium,
                color = SakuroColors.AccentSakura,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SakuroColors.AccentSakura,
                        contentColor = SakuroColors.Background,
                    ),
                ) {
                    Text(stringResource(Res.string.player_up_next_play))
                }
                if (secondsLeft != null) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(Res.string.player_up_next_cancel), color = SakuroColors.TextPrimary)
                    }
                }
            }
        }
    }
}
