package com.rinwave.sakuro.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.ScaleMode
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine

/** Media3 renders into a [PlayerView], which also draws subtitles. */
class Media3SurfaceRenderer : VideoSurfaceRenderer {

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
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
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
