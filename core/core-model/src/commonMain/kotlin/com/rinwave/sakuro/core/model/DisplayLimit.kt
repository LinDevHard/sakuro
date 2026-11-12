package com.rinwave.sakuro.core.model

/** Physical panel size in pixels (orientation does not matter). */
data class DisplaySize(val width: Int, val height: Int)

/** What the screen limit did to the requested upscale; shown to the user. */
data class DisplayLimitStatus(
    val kind: Kind,
    /** Upscale factor the preset asked for; null for an explicit shader chain. */
    val requestedFactor: Float?,
    /** Factor that actually fits the screen (1 when upscaling was skipped). */
    val effectiveFactor: Float,
    /** Height the video occupies on the panel at fit-to-screen. */
    val screenHeight: Int,
) {
    enum class Kind {
        /** Upscale still runs, at a smaller factor that matches the screen. */
        LIMITED,

        /** The screen is not larger than the video: upscaling was dropped. */
        SKIPPED,
    }
}
