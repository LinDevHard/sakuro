package com.rinwave.sakuro.ui

/**
 * Frame scaling mode (FEATURES.md §3.1): fit — fit the whole frame, fill — stretch
 * to fill the screen, zoom — fill the screen while cropping the edges (crop).
 */
enum class ScaleMode {
    FIT,
    FILL,
    ZOOM,
}

val ScaleMode.displayName: String
    get() = when (this) {
        ScaleMode.FIT -> "Fit"
        ScaleMode.FILL -> "Fill"
        ScaleMode.ZOOM -> "Zoom"
    }
