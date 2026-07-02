package com.rinwave.sakuro.core.player

import com.rinwave.sakuro.core.upscale.PlaybackHealth
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Считает [PlaybackHealth] по снимкам [DebugStats]: доля дропнутых кадров
 * за окно между снимками относительно ожидаемого числа кадров (fps × время).
 * Скармливается адаптивному контроллеру (ARCHITECTURE.md §5).
 */
class PlaybackHealthTracker(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private var lastMark: TimeMark? = null
    private var lastDroppedTotal = 0

    fun update(stats: DebugStats): PlaybackHealth {
        val previousMark = lastMark
        lastMark = timeSource.markNow()

        val droppedDelta = (stats.droppedFrames - lastDroppedTotal).coerceAtLeast(0)
        lastDroppedTotal = stats.droppedFrames

        val elapsedMs = previousMark?.elapsedNow()?.inWholeMilliseconds ?: return PlaybackHealth()
        val fps = stats.videoFps?.takeIf { it > 0f } ?: DEFAULT_FPS
        val expectedFrames = fps * elapsedMs / 1000f
        if (expectedFrames <= 0f) return PlaybackHealth()

        return PlaybackHealth(
            droppedFramePercent = (droppedDelta / expectedFrames * 100f).coerceIn(0f, 100f),
        )
    }

    fun reset() {
        lastMark = null
        lastDroppedTotal = 0
    }

    private companion object {
        const val DEFAULT_FPS = 24f
    }
}
