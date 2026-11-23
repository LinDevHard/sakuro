package com.rinwave.sakuro.feature.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Keeps the adaptive upscale loop honest across background playback.
 *
 * In the background no video is decoded, so frame statistics say nothing: feeding them in
 * would let the controller "recover" to a heavier chain (or degrade on stale drops) while
 * nobody watches. Right after returning, the video decoder and the effect graph restart and
 * drop a few frames by design; that burst must not be read as an overloaded GPU.
 */
internal class BackgroundAdaptiveGate(
    private val grace: Duration = 3.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var background = false
    private var resumedAt: TimeMark? = null

    fun onBackground() {
        background = true
        resumedAt = null
    }

    fun onForeground() {
        if (!background) return
        background = false
        resumedAt = timeSource.markNow()
    }

    /** False while hidden: keep the last decision, do not sample. */
    val acceptsDecisions: Boolean get() = !background

    /** False while hidden or during the warm-up right after returning. */
    val trustsFrameStats: Boolean
        get() = !background && (resumedAt?.let { it.elapsedNow() >= grace } ?: true)
}
