package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine

@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier) {
    // FakePlayerEngine рисует синтетический кадр — режим кадра не влияет.
    FakeVideoSurface(engine, modifier)
}
