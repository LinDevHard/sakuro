package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.ScaleMode
import com.rinwave.sakuro.core.player.SubtitlePreferences

/**
 * The current engine's video surface, drawn by the matching registered
 * [VideoSurfaceRenderer]. Engines without one (the desktop FakePlayerEngine)
 * get the synthetic [FakeVideoSurface] (ARCHITECTURE.md §3.2).
 * [scaleMode] — the frame scaling mode, toggled by pinch (FEATURES.md §3.1).
 */
@Composable
fun VideoSurface(
    engine: PlayerEngine,
    scaleMode: ScaleMode,
    modifier: Modifier,
    subtitles: SubtitlePreferences = SubtitlePreferences(),
) {
    val renderer = LocalVideoSurfaceRenderers.current.rendererFor(engine)
    if (renderer != null) {
        renderer.Render(engine, scaleMode, subtitles, modifier)
    } else {
        FakeVideoSurface(engine, modifier)
    }
}
