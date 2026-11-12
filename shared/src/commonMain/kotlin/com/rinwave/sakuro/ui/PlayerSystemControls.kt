package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * System controls for the player's swipe gestures (FEATURES.md §3.1):
 * screen brightness and media volume, values normalized to 0..1.
 */
interface PlayerSystemControls {

    val brightness: Float

    val volume: Float

    fun setBrightness(value: Float)

    fun setVolume(value: Float)
}

/**
 * Android: brightness via the Activity window attributes (reset when leaving the screen),
 * volume via AudioManager/STREAM_MUSIC. Desktop: an in-memory stub.
 */
@Composable
expect fun rememberPlayerSystemControls(): PlayerSystemControls
