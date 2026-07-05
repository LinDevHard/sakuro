package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine

/**
 * The current engine's video surface. Android: Media3 renders into a PlayerView;
 * desktop: the FakePlayerEngine stub (ARCHITECTURE.md §3.2).
 * [scaleMode] — the frame scaling mode, toggled by pinch (FEATURES.md §3.1).
 */
@Composable
expect fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier)
