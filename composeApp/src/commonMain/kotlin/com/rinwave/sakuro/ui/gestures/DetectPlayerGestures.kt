package com.rinwave.sakuro.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/** Колбэки жестов плеера; сессии ([SeekSwipeSession] и т.п.) создаёт вызывающая сторона в on*Start. */
interface PlayerGestureCallbacks {

    fun onSeekStart()

    fun onSeekDrag(totalDxPx: Float)

    fun onSeekEnd()

    /** @param leftSide жест начат в левой половине экрана (яркость), иначе правая (громкость). */
    fun onLevelStart(leftSide: Boolean)

    fun onLevelDrag(totalDyPx: Float)

    fun onLevelEnd()

    /** @param cumulativeZoom произведение zoom-факторов с начала жеста. */
    fun onPinch(cumulativeZoom: Float)

    fun onPinchEnd()
}

private enum class GestureKind { SEEK, LEVEL, PINCH }

/**
 * Свайпы и пинч плеера (FEATURES.md §3.1) поверх detectTapGestures:
 * до порога touchSlop события не потребляются (тапы и long-press живут
 * в соседнем pointerInput), после — потребляются, и тап-детектор отменяется.
 * Если события уже потреблены (long-press ускорение), жест не начинается.
 */
@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // конечный автомат жеста —
// один проход по awaitEachGesture, дробление разорвёт общее состояние (kind/totalPan/cancelled)
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
