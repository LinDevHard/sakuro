package com.rinwave.sakuro.feature.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * On a live stream every upscale-chain change re-prepares the player: seconds of rebuffering
 * and a jump back to the live edge. Automatic (adaptive) changes are therefore spaced out
 * there, while the user's own choices and thermal protection always go through at once.
 */
internal class LiveChainChangeLimiter(
    private val minInterval: Duration = 60.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var lastChange: TimeMark? = null

    fun allow(live: Boolean, userDriven: Boolean, thermal: Boolean): Boolean {
        val allowed = !live || userDriven || thermal ||
            (lastChange?.let { it.elapsedNow() >= minInterval } ?: true)
        if (allowed) lastChange = timeSource.markNow()
        return allowed
    }
}
