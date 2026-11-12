package com.rinwave.sakuro.feature.player

import androidx.compose.runtime.Composable

/**
 * Controls immersive mode: when [enabled], the system bars
 * (status bar + navigation) are hidden and shown by a swipe from the screen edge;
 * with [enabled] = false they come back.
 * Desktop: no-op.
 */
@Composable
internal expect fun ImmersiveMode(enabled: Boolean)
