package com.rinwave.sakuro.playback

import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSessionStateTest {
    @Test
    fun promptAndUnsettledSeekCannotOverwriteSavedProgress() {
        val session = PlaybackSessionState(30_000)
        val zero = PlayerState(status = PlaybackStatus.READY, durationMs = 100_000)
        assertNull(session.checkpoint(zero, force = true))
        assertEquals(30_000L, session.chooseResume(true))
        assertNull(session.checkpoint(zero, force = true))
        assertEquals(30_000L, session.takePendingSeek(zero))
        assertEquals(30_000L, session.checkpoint(zero.copy(positionMs = 30_000), force = true)?.positionMs)
    }

    @Test
    fun restartAndPeriodicCheckpointsAreExplicitAndThrottled() {
        val session = PlaybackSessionState(30_000)
        assertEquals(0L, session.chooseResume(false))
        val state = PlayerState(status = PlaybackStatus.READY, positionMs = 1_000, durationMs = 100_000)
        assertEquals(0L, session.takePendingSeek(state))
        assertNull(session.checkpoint(state))
        assertEquals(1_000L, session.checkpoint(state, force = true)?.positionMs)
        assertNull(session.checkpoint(state.copy(positionMs = 2_000)))
        assertEquals(7_000L, session.checkpoint(state.copy(positionMs = 7_000))?.positionMs)
    }

    @Test
    fun autoplayRunsOnceAtEndOnlyWhenEnabledAndNextFileExists() {
        val session = PlaybackSessionState(0)
        assertFalse(session.shouldAdvance(PlaybackStatus.ENDED, false, true))
        assertFalse(session.shouldAdvance(PlaybackStatus.ENDED, true, false))
        assertTrue(session.shouldAdvance(PlaybackStatus.ENDED, true, true))
        assertFalse(session.shouldAdvance(PlaybackStatus.ENDED, true, true))
        assertFalse(session.shouldAdvance(PlaybackStatus.READY, true, true))
        assertTrue(session.shouldAdvance(PlaybackStatus.ENDED, true, true))
    }

    @Test
    fun earlyChoiceDefersSeekAndCheckpointsUntilReady() {
        val session = PlaybackSessionState(30_000)
        session.chooseResume(true)
        val loading = PlayerState(status = PlaybackStatus.BUFFERING, durationMs = 100_000)
        assertNull(session.takePendingSeek(loading))
        assertNull(session.checkpoint(loading, force = true))
        assertFalse(session.shouldAdvance(PlaybackStatus.ENDED, true, true))
        assertNull(session.takePendingSeek(loading.copy(status = PlaybackStatus.READY, durationMs = 0)))
        val ready = loading.copy(status = PlaybackStatus.READY)
        assertEquals(30_000L, session.takePendingSeek(ready))
        assertNull(session.takePendingSeek(ready))
        assertNull(session.checkpoint(ready, force = true))
        assertEquals(31_000L, session.checkpoint(ready.copy(positionMs = 31_000), force = true)?.positionMs)
    }

    @Test
    fun shortenedFileClampsResumeToPlayablePosition() {
        val session = PlaybackSessionState(90_000)
        session.chooseResume(true)
        val ready = PlayerState(status = PlaybackStatus.READY, durationMs = 40_000)
        assertEquals(35_000L, session.takePendingSeek(ready))
        assertEquals(35_000L, session.checkpoint(ready.copy(positionMs = 35_000), force = true)?.positionMs)
    }

    @Test
    fun repeatedEndedUpdatesDoNotRewriteHistory() {
        val session = PlaybackSessionState(0)
        val ended = PlayerState(status = PlaybackStatus.ENDED, positionMs = 100_000, durationMs = 100_000)
        assertEquals(100_000L, session.checkpoint(ended)?.positionMs)
        assertNull(session.checkpoint(ended))
        assertNull(session.checkpoint(ended, force = true))
    }
}
