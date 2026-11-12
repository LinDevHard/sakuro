package com.rinwave.sakuro.feature.player

import androidx.compose.runtime.Composable

/**
 * System controls for the player's swipe gestures (docs/product/features.md §3.1):
 * screen brightness and media volume, values normalized to 0..1.
 */
internal interface PlayerSystemControls {

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
internal expect fun rememberPlayerSystemControls(): PlayerSystemControls
