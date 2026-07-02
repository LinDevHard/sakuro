package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine

/**
 * Видео-поверхность текущего движка. Android: Media3 рендерит в PlayerView;
 * desktop: заглушка FakePlayerEngine (ARCHITECTURE.md §3.2).
 * [scaleMode] — режим кадра, переключается пинчем (FEATURES.md §3.1).
 */
@Composable
expect fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier)
