package com.rinwave.sakuro.playback

import com.rinwave.sakuro.core.media.PlaybackProgress
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.player.PlayerState
import kotlin.math.abs

/** Resume/checkpoint policy independent of UI and the native player. */
internal class PlaybackSessionState(private val savedPositionMs: Long) {
    var awaitingChoice = savedPositionMs > 0
        private set
    private var seekTarget: Long? = null
    private var pendingSeek: Long? = null
    private var lastCheckpoint = 0L
    private var lastCheckpointDuration = 0L
    private var advanced = false

    fun chooseResume(continueWatching: Boolean): Long {
        awaitingChoice = false
        val target = if (continueWatching) savedPositionMs else 0L
        pendingSeek = target
        return target
    }

    /** Native engines may discard seek commands before the media is loaded. */
    fun takePendingSeek(state: PlayerState): Long? {
        val requested = pendingSeek ?: return null
        if (state.status != PlaybackStatus.READY || state.durationMs <= 0) return null
        val target = requested.coerceIn(0L, (state.durationMs - SAVE_STEP_MS).coerceAtLeast(0L))
        pendingSeek = null
        seekTarget = target
        lastCheckpoint = target
        return target
    }

    fun checkpoint(state: PlayerState, force: Boolean = false): PlaybackProgress? {
        if (!canCheckpoint(state, force)) return null
        val position = if (state.status == PlaybackStatus.ENDED) state.durationMs else state.positionMs
        if (position < 0) return null
        if (alreadySavedEnd(state, position)) return null
        lastCheckpoint = position
        lastCheckpointDuration = state.durationMs
        return PlaybackProgress(position, state.durationMs)
    }

    private fun canCheckpoint(state: PlayerState, force: Boolean): Boolean {
        if (awaitingChoice || pendingSeek != null) return false
        if (state.durationMs <= 0 || state.status == PlaybackStatus.ERROR) return false
        if (!hasSettledSeek(state)) return false
        return force || state.status == PlaybackStatus.ENDED ||
            abs(state.positionMs - lastCheckpoint) >= SAVE_STEP_MS
    }

    private fun hasSettledSeek(state: PlayerState): Boolean {
        val target = seekTarget ?: return true
        if (state.status != PlaybackStatus.ENDED && abs(state.positionMs - target) > SEEK_TOLERANCE_MS) {
            return false
        }
        seekTarget = null
        return true
    }

    private fun alreadySavedEnd(state: PlayerState, position: Long): Boolean =
        state.status == PlaybackStatus.ENDED && position == lastCheckpoint &&
            state.durationMs == lastCheckpointDuration

    fun shouldAdvance(status: PlaybackStatus, enabled: Boolean, hasNext: Boolean): Boolean {
        if (status != PlaybackStatus.ENDED) advanced = false
        if (awaitingChoice || pendingSeek != null || advanced) return false
        if (status != PlaybackStatus.ENDED || !enabled || !hasNext) return false
        advanced = true
        return true
    }

    private companion object {
        const val SAVE_STEP_MS = 5_000L
        const val SEEK_TOLERANCE_MS = 3_000L
    }
}
