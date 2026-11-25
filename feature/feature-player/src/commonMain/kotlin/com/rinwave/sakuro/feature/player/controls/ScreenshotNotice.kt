package com.rinwave.sakuro.feature.player.controls

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.rinwave.sakuro.core.designsystem.SakuroColors
import com.rinwave.sakuro.feature.player.resources.Res
import com.rinwave.sakuro.feature.player.resources.player_screenshot_failed
import com.rinwave.sakuro.feature.player.resources.player_screenshot_saved
import com.rinwave.sakuro.feature.player.resources.player_screenshot_share
import org.jetbrains.compose.resources.stringResource

internal sealed interface ScreenshotResult {
    data class Saved(val uri: String, val image: ImageBitmap) : ScreenshotResult
    data object Failed : ScreenshotResult
}

/** A short confirmation with the captured frame and a share action. */
@Composable
internal fun ScreenshotNotice(result: ScreenshotResult, onShare: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = SakuroColors.SurfaceElevated.copy(alpha = 0.94f),
        contentColor = SakuroColors.TextPrimary,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.widthIn(max = 360.dp),
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (result) {
                is ScreenshotResult.Saved -> {
                    Image(
                        bitmap = result.image,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        stringResource(Res.string.player_screenshot_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = { onShare(result.uri) }) {
                        Text(stringResource(Res.string.player_screenshot_share), color = SakuroColors.AccentSakura)
                    }
                }
                ScreenshotResult.Failed -> Text(
                    stringResource(Res.string.player_screenshot_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
