package com.rinwave.sakuro.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/** Player gesture callbacks; sessions ([SeekSwipeSession] etc.) are created by the caller in on*Start. */
interface PlayerGestureCallbacks {

    fun onSeekStart()

    fun onSeekDrag(totalDxPx: Float)

    fun onSeekEnd()

    /** @param leftSide the gesture started in the left half of the screen (brightness), otherwise the right (volume). */
    fun onLevelStart(leftSide: Boolean)

    fun onLevelDrag(totalDyPx: Float)

    fun onLevelEnd()

    /** @param cumulativeZoom the product of zoom factors since the gesture started. */
    fun onPinch(cumulativeZoom: Float)

    fun onPinchEnd()
}

private enum class GestureKind { SEEK, LEVEL, PINCH }

/**
 * Player swipes and pinch (FEATURES.md §3.1) on top of detectTapGestures:
 * below the touchSlop threshold events are not consumed (taps and long-press live
 * in a sibling pointerInput); above it they are consumed and the tap detector is cancelled.
 * If events are already consumed (long-press speed-up), the gesture does not start.
 */
@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // the gesture state machine —
// a single pass over awaitEachGesture; splitting it would break the shared state (kind/totalPan/cancelled)
suspend fun PointerInputScope.detectPlayerGestures(callbacks: PlayerGestureCallbacks) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var kind: GestureKind? = null
        var totalPan = Offset.Zero
        var totalZoom = 1f
        var cancelled = false

        while (true) {
            val event = awaitPointerEvent()
            val anyPressed = event.changes.any { it.pressed }
            if (kind == null && event.changes.any { it.isConsumed }) cancelled = true
            if (cancelled) {
                if (!anyPressed) break else continue
            }
            if (!anyPressed) break

            totalZoom *= event.calculateZoom()
            totalPan += event.calculatePan()

            if (kind == null) {
                val slop = viewConfiguration.touchSlop
                val pointerCount = event.changes.count { it.pressed }
                kind = when {
                    pointerCount > 1 -> GestureKind.PINCH
                    abs(totalPan.x) > slop && abs(totalPan.x) > abs(totalPan.y) -> GestureKind.SEEK
                    abs(totalPan.y) > slop && abs(totalPan.y) > abs(totalPan.x) -> GestureKind.LEVEL
                    else -> null
                }
                when (kind) {
                    GestureKind.SEEK -> callbacks.onSeekStart()
                    GestureKind.LEVEL -> callbacks.onLevelStart(down.position.x < size.width / 2f)
                    else -> Unit
                }
            }

            if (kind != null) {
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                when (kind) {
                    GestureKind.SEEK -> callbacks.onSeekDrag(totalPan.x)
                    GestureKind.LEVEL -> callbacks.onLevelDrag(totalPan.y)
                    GestureKind.PINCH -> callbacks.onPinch(totalZoom)
                }
            }
        }

        when (kind) {
            GestureKind.SEEK -> callbacks.onSeekEnd()
            GestureKind.LEVEL -> callbacks.onLevelEnd()
            GestureKind.PINCH -> callbacks.onPinchEnd()
            null -> Unit
        }
    }
}
