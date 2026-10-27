package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.SubtitlePreferences

@Composable
actual fun VideoSurface(
    engine: PlayerEngine,
    scaleMode: ScaleMode,
    modifier: Modifier,
    subtitles: SubtitlePreferences,
) {
    // FakePlayerEngine draws a synthetic frame — the scaling mode has no effect.
    FakeVideoSurface(engine, modifier)
}
