package com.rinwave.sakuro.feature.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BackgroundAdaptiveGateTest {

    private val time = TestTimeSource()
    private val gate = BackgroundAdaptiveGate(grace = 3.seconds, timeSource = time)

    @Test
    fun `foreground playback trusts stats and accepts decisions`() {
        assertTrue(gate.acceptsDecisions)
        assertTrue(gate.trustsFrameStats)
    }

    @Test
    fun `background freezes the adaptive loop`() {
        gate.onBackground()
        assertFalse(gate.acceptsDecisions)
        assertFalse(gate.trustsFrameStats)
    }

    @Test
    fun `returning ignores frame drops until the grace period ends`() {
        gate.onBackground()
        time += 60.seconds
        gate.onForeground()
        assertTrue(gate.acceptsDecisions)
        assertFalse(gate.trustsFrameStats)
        time += 2.seconds
        assertFalse(gate.trustsFrameStats)
        time += 1.seconds
        assertTrue(gate.trustsFrameStats)
    }

    @Test
    fun `a start without a preceding background is not a resume`() {
        gate.onForeground()
        assertTrue(gate.trustsFrameStats)
    }
}
