package com.rinwave.sakuro.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.model.FrameRateMatching
import com.rinwave.sakuro.core.model.ScaleMode
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.ui.VideoSurfaceRenderer
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * Media3 renders into a [PlayerView], which also draws subtitles. The display refresh rate
 * follows the video according to [frameRateMatching].
 */
class Media3SurfaceRenderer(
    private val frameRateMatching: StateFlow<FrameRateMatching>,
) : VideoSurfaceRenderer {

    override fun canRender(engine: PlayerEngine): Boolean = engine is Media3PlayerEngine

    @OptIn(UnstableApi::class)
    @Composable
    override fun Render(
        engine: PlayerEngine,
        scaleMode: ScaleMode,
        subtitles: SubtitlePreferences,
        modifier: Modifier,
    ) {
        val media3 = engine as Media3PlayerEngine
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        val fps by media3.videoFrameRate.collectAsState()
        val matching by frameRateMatching.collectAsState()
        playerView?.let { view ->
            val controller = remember(view) { DisplayFrameRateController(view) }
            DisposableEffect(controller) { onDispose { controller.release() } }
            LaunchedEffect(controller, fps, matching) { controller.update(fps, matching) }
        }
        AndroidView(
            factory = { context ->
                PlayerView(context).also { playerView = it }.apply {
                    // libass draws ASS/SSA in its own layer inside the subtitle view.
                    subtitleView?.let(media3::attachAssOverlay)
                    useController = false
                    resizeMode = scaleMode.toResizeMode()
                    keepScreenOn = true
                    player = media3.player
                    applySubtitlePreferences(subtitles)
                }
            },
            update = { view ->
                view.player = media3.player
                view.resizeMode = scaleMode.toResizeMode()
                view.applySubtitlePreferences(subtitles)
            },
            modifier = modifier,
        )
    }
}

@UnstableApi
private fun ScaleMode.toResizeMode(): Int = when (this) {
    ScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    ScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    ScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

@UnstableApi
private fun PlayerView.applySubtitlePreferences(preferences: SubtitlePreferences) {
    val value = preferences.normalized()
    subtitleView?.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, value.fontSizeSp)
        setBottomPaddingFraction(value.bottomPaddingFraction)
        setStyle(
            androidx.media3.ui.CaptionStyleCompat(
                android.graphics.Color.WHITE,
                if (value.backgroundEnabled) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null,
            ),
        )
    }
}
