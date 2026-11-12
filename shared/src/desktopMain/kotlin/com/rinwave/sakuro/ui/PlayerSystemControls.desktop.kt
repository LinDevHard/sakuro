package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Desktop stub: values live in memory, we do not touch the system controls. */
@Composable
actual fun rememberPlayerSystemControls(): PlayerSystemControls =
    remember { InMemoryPlayerSystemControls() }

private class InMemoryPlayerSystemControls : PlayerSystemControls {

    override var brightness: Float = 0.5f
        private set

    override var volume: Float = 0.5f
        private set

    override fun setBrightness(value: Float) {
        brightness = value.coerceIn(0f, 1f)
    }

    override fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }
}
