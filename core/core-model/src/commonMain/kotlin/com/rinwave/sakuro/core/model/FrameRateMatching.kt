package com.rinwave.sakuro.core.model

/** How the display refresh rate follows the video frame rate (24p judder on 60 Hz screens). */
enum class FrameRateMatching {
    /** Never touch the display rate. */
    OFF,

    /** Only switch when the display can do it without a visible blink (most 90/120 Hz phones). */
    SEAMLESS,

    /** Switch even if the screen blanks for a moment (TVs, some phones); best for films. */
    ALWAYS,
}
