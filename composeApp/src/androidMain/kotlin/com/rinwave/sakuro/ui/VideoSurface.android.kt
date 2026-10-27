package com.rinwave.sakuro.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine
import com.rinwave.sakuro.referenceVideoSurface

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(
    engine: PlayerEngine,
    scaleMode: ScaleMode,
    modifier: Modifier,
    subtitles: SubtitlePreferences,
) {
    if (referenceVideoSurface(engine, scaleMode, modifier)) return
    when (engine) {
        is Media3PlayerEngine -> AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = scaleMode.toResizeMode()
                    keepScreenOn = true
                    player = engine.player
                    applySubtitlePreferences(subtitles)
                }
            },
            update = { view ->
                view.player = engine.player
                view.resizeMode = scaleMode.toResizeMode()
                view.applySubtitlePreferences(subtitles)
            },
            modifier = modifier,
        )

        else -> FakeVideoSurface(engine, modifier)
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
