package com.rinwave.sakuro.ui.gestures

import com.rinwave.sakuro.ui.ScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekSwipeSessionTest {

    private fun session(startMs: Long = 60_000, durationMs: Long = 600_000, widthPx: Float = 1000f) =
        SeekSwipeSession(startMs, durationMs, widthPx)

    @Test
    fun `a full-width swipe seeks one full window forward`() {
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS, session().positionFor(1000f))
    }

    @Test
    fun `a leftward swipe rewinds proportionally`() {
        assertEquals(60_000 - SeekSwipeSession.FULL_WIDTH_SEEK_MS / 2, session().positionFor(-500f))
    }

    @Test
    fun `no displacement keeps the position`() {
        assertEquals(60_000, session().positionFor(0f))
    }

    @Test
    fun `seeking below zero clamps to zero`() {
        assertEquals(0, session(startMs = 5_000).positionFor(-1000f))
    }

    @Test
    fun `seeking past the end clamps to the duration`() {
        assertEquals(600_000, session(startMs = 590_000).positionFor(1000f))
    }

    @Test
    fun `with unknown duration the seek is unbounded`() {
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS, session(durationMs = 0).positionFor(1000f))
    }

    @Test
    fun `sensitivity scales the seek distance`() {
        val half = SeekSwipeSession(60_000, 600_000, 1000f, sensitivity = 0.5f)
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS / 2, half.positionFor(1000f))

        val double = SeekSwipeSession(60_000, 600_000, 1000f, sensitivity = 2f)
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS * 2, double.positionFor(1000f))
    }
}

class LevelSwipeSessionTest {

    @Test
    fun `swiping up raises the level`() {
        assertEquals(0.75f, LevelSwipeSession(0.5f, 1000f).levelFor(-250f))
    }

    @Test
    fun `swiping down lowers the level`() {
        assertEquals(0.25f, LevelSwipeSession(0.5f, 1000f).levelFor(250f))
    }

    @Test
    fun `the level is clamped to 0-1`() {
        assertEquals(1f, LevelSwipeSession(0.9f, 1000f).levelFor(-500f))
        assertEquals(0f, LevelSwipeSession(0.1f, 1000f).levelFor(500f))
    }

    @Test
    fun `sensitivity scales the level change`() {
        assertEquals(0.625f, LevelSwipeSession(0.5f, 1000f, sensitivity = 0.5f).levelFor(-250f))
        assertEquals(1f, LevelSwipeSession(0.5f, 1000f, sensitivity = 2f).levelFor(-250f))
    }
}

class PinchZoomSessionTest {

    private val threshold = PinchZoomSession.EXPAND_THRESHOLD

    @Test
    fun `expanding past the threshold switches from fit to zoom`() {
        val session = PinchZoomSession(ScaleMode.FIT, startManual = 1f)
        // Below the threshold we stay in fit.
        assertEquals(PinchZoomState(ScaleMode.FIT, 1f), session.update(1.1f))
        // Crossing the threshold switches to zoom, and the mode persists for the gesture.
        assertEquals(ScaleMode.ZOOM, session.update(threshold + 0.01f).mode)
    }

    @Test
    fun `within zoom the pinch scales the manual factor`() {
        val session = PinchZoomSession(ScaleMode.ZOOM, startManual = 1f)
        // Doubling the pinch within zoom yields a manual factor of 2.0.
        assertEquals(PinchZoomState(ScaleMode.ZOOM, 2f), session.update(2f))
    }

    @Test
    fun `pinching back steps down and returns to fit`() {
        // Pinching halves within zoom; from 2x it drops to the lower zoom bound (manual 1).
        assertEquals(
            PinchZoomState(ScaleMode.ZOOM, 1f),
            PinchZoomSession(ScaleMode.ZOOM, startManual = 2f).update(0.5f),
        )
        // Pinching further from fit collapses back below the threshold to fit.
        assertEquals(
            PinchZoomState(ScaleMode.FIT, 1f),
            PinchZoomSession(ScaleMode.ZOOM, startManual = 1f).update(0.5f),
        )
    }

    @Test
    fun `the manual factor is clamped to the maximum`() {
        val session = PinchZoomSession(ScaleMode.ZOOM, startManual = 1f)
        assertEquals(PinchZoomSession.MAX_MANUAL, session.update(100f).manual)
    }

    @Test
    fun `pinching in while in fit stays in fit`() {
        val session = PinchZoomSession(ScaleMode.FIT, startManual = 1f)
        assertEquals(PinchZoomState(ScaleMode.FIT, 1f), session.update(0.3f))
    }
}
