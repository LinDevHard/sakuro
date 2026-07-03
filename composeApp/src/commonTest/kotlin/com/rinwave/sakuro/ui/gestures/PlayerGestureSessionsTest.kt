package com.rinwave.sakuro.ui.gestures

import com.rinwave.sakuro.ui.ScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekSwipeSessionTest {

    private fun session(startMs: Long = 60_000, durationMs: Long = 600_000, widthPx: Float = 1000f) =
        SeekSwipeSession(startMs, durationMs, widthPx)

    @Test
    fun `свайп вправо на всю ширину даёт полный шаг перемотки`() {
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS, session().positionFor(1000f))
    }

    @Test
    fun `свайп влево на полширины даёт минус половину шага`() {
        assertEquals(60_000 - SeekSwipeSession.FULL_WIDTH_SEEK_MS / 2, session().positionFor(-500f))
    }

    @Test
    fun `нулевое смещение не меняет позицию`() {
        assertEquals(60_000, session().positionFor(0f))
    }

    @Test
    fun `позиция не уходит ниже нуля`() {
        assertEquals(0, session(startMs = 5_000).positionFor(-1000f))
    }

    @Test
    fun `позиция не превышает длительность`() {
        assertEquals(600_000, session(startMs = 590_000).positionFor(1000f))
    }

    @Test
    fun `при неизвестной длительности перемотка вперёд не ограничивается`() {
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS, session(durationMs = 0).positionFor(1000f))
    }

    @Test
    fun `чувствительность масштабирует шаг перемотки`() {
        val half = SeekSwipeSession(60_000, 600_000, 1000f, sensitivity = 0.5f)
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS / 2, half.positionFor(1000f))

        val double = SeekSwipeSession(60_000, 600_000, 1000f, sensitivity = 2f)
        assertEquals(60_000 + SeekSwipeSession.FULL_WIDTH_SEEK_MS * 2, double.positionFor(1000f))
    }
}

class LevelSwipeSessionTest {

    @Test
    fun `движение вверх увеличивает уровень`() {
        assertEquals(0.75f, LevelSwipeSession(0.5f, 1000f).levelFor(-250f))
    }

    @Test
    fun `движение вниз уменьшает уровень`() {
        assertEquals(0.25f, LevelSwipeSession(0.5f, 1000f).levelFor(250f))
    }

    @Test
    fun `уровень зажат в диапазон 0-1`() {
        assertEquals(1f, LevelSwipeSession(0.9f, 1000f).levelFor(-500f))
        assertEquals(0f, LevelSwipeSession(0.1f, 1000f).levelFor(500f))
    }

    @Test
    fun `чувствительность масштабирует изменение уровня`() {
        assertEquals(0.625f, LevelSwipeSession(0.5f, 1000f, sensitivity = 0.5f).levelFor(-250f))
        assertEquals(1f, LevelSwipeSession(0.5f, 1000f, sensitivity = 2f).levelFor(-250f))
    }
}

class PinchSessionTest {

    @Test
    fun `раздвигание пальцев шагает fit-fill-zoom`() {
        val session = PinchSession(ScaleMode.FIT)
        assertEquals(ScaleMode.FILL, session.update(1.4f))
        assertEquals(ScaleMode.ZOOM, session.update(1.4f * 1.4f))
    }

    @Test
    fun `сведение пальцев шагает обратно`() {
        val session = PinchSession(ScaleMode.ZOOM)
        assertEquals(ScaleMode.FILL, session.update(0.7f))
        assertEquals(ScaleMode.FIT, session.update(0.7f * 0.7f))
    }

    @Test
    fun `малое изменение не переключает режим`() {
        val session = PinchSession(ScaleMode.FIT)
        assertEquals(ScaleMode.FIT, session.update(1.1f))
        assertEquals(ScaleMode.FIT, session.update(0.95f))
    }

    @Test
    fun `режим не выходит за края списка`() {
        assertEquals(ScaleMode.FIT, PinchSession(ScaleMode.FIT).update(0.5f))
        assertEquals(ScaleMode.ZOOM, PinchSession(ScaleMode.ZOOM).update(2f))
    }

    @Test
    fun `после шага база сбрасывается и нужен новый порог`() {
        val session = PinchSession(ScaleMode.FIT)
        assertEquals(ScaleMode.FILL, session.update(1.4f))
        // Относительно новой базы 1.4 фактор 1.5/1.4 ≈ 1.07 — ниже порога.
        assertEquals(ScaleMode.FILL, session.update(1.5f))
    }
}
