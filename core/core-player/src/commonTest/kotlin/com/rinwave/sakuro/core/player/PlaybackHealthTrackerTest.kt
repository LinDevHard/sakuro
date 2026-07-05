package com.rinwave.sakuro.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class PlaybackHealthTrackerTest {

    private val timeSource = TestTimeSource()
    private val tracker = PlaybackHealthTracker(timeSource)

    @Test
    fun firstSampleIsAlwaysHealthy() {
        val health = tracker.update(DebugStats(droppedFrames = 100, videoFps = 24f))
        assertEquals(0f, health.droppedFramePercent)
    }

    @Test
    fun noDropsMeansZeroPercent() {
        tracker.update(DebugStats(droppedFrames = 0, videoFps = 24f))
        timeSource += 1000.milliseconds
        val health = tracker.update(DebugStats(droppedFrames = 0, videoFps = 24f))
        assertEquals(0f, health.droppedFramePercent)
    }

    @Test
    fun dropsAreRelativeToExpectedFrames() {
        tracker.update(DebugStats(droppedFrames = 0, videoFps = 24f))
        timeSource += 1000.milliseconds
        // 6 drops out of 24 expected frames per second = 25%.
        val health = tracker.update(DebugStats(droppedFrames = 6, videoFps = 24f))
        assertEquals(25f, health.droppedFramePercent)
    }

    @Test
    fun percentIsCappedAtHundred() {
        tracker.update(DebugStats(droppedFrames = 0, videoFps = 24f))
        timeSource += 100.milliseconds
        val health = tracker.update(DebugStats(droppedFrames = 500, videoFps = 24f))
        assertEquals(100f, health.droppedFramePercent)
    }

    @Test
    fun counterResetInEngineDoesNotProduceNegativeDelta() {
        tracker.update(DebugStats(droppedFrames = 50, videoFps = 24f))
        timeSource += 1000.milliseconds
        // The engine reset the counter (new load) — we do not count these as drops.
        val health = tracker.update(DebugStats(droppedFrames = 0, videoFps = 24f))
        assertEquals(0f, health.droppedFramePercent)
    }

    @Test
    fun resetForgetsHistory() {
        tracker.update(DebugStats(droppedFrames = 10, videoFps = 24f))
        tracker.reset()
        val health = tracker.update(DebugStats(droppedFrames = 20, videoFps = 24f))
        assertEquals(0f, health.droppedFramePercent)
    }

    @Test
    fun unknownFpsFallsBackToDefault() {
        tracker.update(DebugStats(droppedFrames = 0))
        timeSource += 1000.milliseconds
        val health = tracker.update(DebugStats(droppedFrames = 12))
        assertTrue(health.droppedFramePercent > 0f)
    }
}
