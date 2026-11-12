package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.model.ScaleMode
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.player.PlayerEngine

/**
 * Draws one engine family's video output. The app module registers one per
 * engine it ships (Media3 always, libmpv only in the full flavor), so UI code
 * never needs to know concrete engine classes.
 */
interface VideoSurfaceRenderer {
    fun canRender(engine: PlayerEngine): Boolean

    @Composable
    fun Render(
        engine: PlayerEngine,
        scaleMode: ScaleMode,
        subtitles: SubtitlePreferences,
        modifier: Modifier,
    )
}

/** The first renderer that accepts [engine], in registration order. */
fun List<VideoSurfaceRenderer>.rendererFor(engine: PlayerEngine): VideoSurfaceRenderer? =
    firstOrNull { it.canRender(engine) }

/** Renderers registered by the app; empty means every engine falls back to [FakeVideoSurface]. */
val LocalVideoSurfaceRenderers = staticCompositionLocalOf<List<VideoSurfaceRenderer>> { emptyList() }
