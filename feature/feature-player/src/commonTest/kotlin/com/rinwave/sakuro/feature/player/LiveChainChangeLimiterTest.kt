package com.rinwave.sakuro.feature.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LiveChainChangeLimiterTest {

    private val time = TestTimeSource()
    private val limiter = LiveChainChangeLimiter(minInterval = 60.seconds, timeSource = time)

    @Test
    fun `video on demand is never limited`() {
        repeat(3) { assertTrue(limiter.allow(live = false, userDriven = false, thermal = false)) }
    }

    @Test
    fun `automatic live changes are spaced out`() {
        assertTrue(limiter.allow(live = true, userDriven = false, thermal = false))
        time += 10.seconds
        assertFalse(limiter.allow(live = true, userDriven = false, thermal = false))
        time += 50.seconds
        assertTrue(limiter.allow(live = true, userDriven = false, thermal = false))
    }

    @Test
    fun `user choices and thermal protection go through at once`() {
        assertTrue(limiter.allow(live = true, userDriven = false, thermal = false))
        assertTrue(limiter.allow(live = true, userDriven = true, thermal = false))
        assertTrue(limiter.allow(live = true, userDriven = false, thermal = true))
    }
}
