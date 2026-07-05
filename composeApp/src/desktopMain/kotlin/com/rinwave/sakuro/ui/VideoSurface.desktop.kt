package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine

@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier) {
    // FakePlayerEngine draws a synthetic frame — the scaling mode has no effect.
    FakeVideoSurface(engine, modifier)
}
