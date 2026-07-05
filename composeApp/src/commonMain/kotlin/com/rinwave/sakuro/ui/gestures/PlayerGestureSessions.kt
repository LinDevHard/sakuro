package com.rinwave.sakuro.ui.gestures

import com.rinwave.sakuro.ui.ScaleMode

/**
 * Pure math for player gestures (FEATURES.md §3.1): a session lives from the start
 * to the end of one gesture and turns the finger's total displacement into a value.
 * No Compose dependencies — the logic is covered by unit tests on desktop.
 */

/**
 * Horizontal swipe — seek: a full-width swipe corresponds to
 * ±[fullWidthSeekMs] × [sensitivity] from the position at the gesture start.
 */
class SeekSwipeSession(
    private val startPositionMs: Long,
    private val durationMs: Long,
    private val widthPx: Float,
    private val sensitivity: Float = 1f,
    private val fullWidthSeekMs: Long = FULL_WIDTH_SEEK_MS,
) {
    fun positionFor(totalDxPx: Float): Long {
        val delta = (totalDxPx / widthPx * fullWidthSeekMs * sensitivity).toLong()
        val upperBound = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return (startPositionMs + delta).coerceIn(0L, upperBound)
    }

    companion object {
        const val FULL_WIDTH_SEEK_MS = 90_000L
    }
}

/**
 * Vertical swipe — brightness/volume: a full-height swipe covers
 * the range 0..1 × [sensitivity]; moving up increases the value.
 */
class LevelSwipeSession(
    private val startLevel: Float,
    private val heightPx: Float,
    private val sensitivity: Float = 1f,
) {
    fun levelFor(totalDyPx: Float): Float =
        (startLevel - totalDyPx / heightPx * sensitivity).coerceIn(0f, 1f)
}

/** Pinch result: the frame scaling mode plus the manual zoom factor inside it. */
data class PinchZoomState(val mode: ScaleMode, val manual: Float)

/**
 * Pinch to zoom, YouTube-style. The pinch maps onto two frame modes:
 * fit (FIT) below the threshold and fill-with-crop (ZOOM) above it. Within ZOOM
 * the extra pinch drives [manual] (1.0..[MAX_MANUAL]) as the zoom factor. At the
 * boundary: expanding past the threshold enters zoom (manual=1), and pinching
 * back returns to fit.
 *
 * The session keeps only the accumulated factor x
 * (x < [EXPAND_THRESHOLD] → FIT; x ≥ threshold → ZOOM with manual = x / threshold), so
 * the logic is covered by unit tests without Compose.
 */
class PinchZoomSession(startMode: ScaleMode, startManual: Float) {

    private val startX: Float = if (startMode == ScaleMode.ZOOM) {
        EXPAND_THRESHOLD * startManual.coerceAtLeast(1f)
    } else {
        FIT_ANCHOR
    }

    /** @param cumulativeZoom the product of zoom factors since the gesture start (1f = no change). */
    fun update(cumulativeZoom: Float): PinchZoomState {
        val x = (startX * cumulativeZoom).coerceIn(X_MIN, EXPAND_THRESHOLD * MAX_MANUAL)
        return if (x < EXPAND_THRESHOLD) {
            PinchZoomState(ScaleMode.FIT, 1f)
        } else {
            PinchZoomState(ScaleMode.ZOOM, (x / EXPAND_THRESHOLD).coerceIn(1f, MAX_MANUAL))
        }
    }

    companion object {
        /** Accumulated factor at which we switch to zoom; below it we snap back to fit. */
        const val EXPAND_THRESHOLD = 1.3f

        /** Maximum manual zoom factor within the zoom mode. */
        const val MAX_MANUAL = 4f

        private const val FIT_ANCHOR = 1f
        private const val X_MIN = 0.5f
    }
}
