package com.rinwave.sakuro.ui.gestures

import com.rinwave.sakuro.ui.ScaleMode

/**
 * Чистая математика жестов плеера (FEATURES.md §3.1): сессия живёт от начала
 * до конца одного жеста и превращает суммарное смещение пальца в значение.
 * Compose-зависимостей нет — логика покрывается unit-тестами на desktop.
 */

/**
 * Горизонтальный свайп — перемотка: свайп на всю ширину экрана
 * соответствует ±[fullWidthSeekMs] × [sensitivity] от позиции на момент начала жеста.
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
 * Вертикальный свайп — яркость/громкость: свайп на всю высоту экрана
 * проходит диапазон 0..1 × [sensitivity], движение вверх увеличивает значение.
 */
class LevelSwipeSession(
    private val startLevel: Float,
    private val heightPx: Float,
    private val sensitivity: Float = 1f,
) {
    fun levelFor(totalDyPx: Float): Float =
        (startLevel - totalDyPx / heightPx * sensitivity).coerceIn(0f, 1f)
}

/**
 * Пинч — переключение режима кадра: раздвигание пальцев шагает
 * FIT → FILL → ZOOM, сведение — обратно. После каждого шага база
 * сбрасывается, чтобы один длинный пинч мог пройти несколько режимов.
 */
class PinchSession(initial: ScaleMode) {

    var mode: ScaleMode = initial
        private set

    private var baselineZoom = 1f

    /** @param cumulativeZoom произведение zoom-факторов с начала жеста (1f = без изменений). */
    fun update(cumulativeZoom: Float): ScaleMode {
        val relative = cumulativeZoom / baselineZoom
        when {
            relative > STEP_UP_FACTOR && mode.ordinal < ScaleMode.entries.lastIndex -> {
                mode = ScaleMode.entries[mode.ordinal + 1]
                baselineZoom = cumulativeZoom
            }

            relative < STEP_DOWN_FACTOR && mode.ordinal > 0 -> {
                mode = ScaleMode.entries[mode.ordinal - 1]
                baselineZoom = cumulativeZoom
            }
        }
        return mode
    }

    companion object {
        const val STEP_UP_FACTOR = 1.3f
        const val STEP_DOWN_FACTOR = 1f / STEP_UP_FACTOR
    }
}
