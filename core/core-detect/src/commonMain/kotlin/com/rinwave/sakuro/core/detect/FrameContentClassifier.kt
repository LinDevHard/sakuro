package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Второй слой детекции (FEATURES.md §1): статистика по сэмплам кадров.
 * Рисованный контент отличают от съёмки три признака, устойчивые к даунскейлу:
 * плоские заливки (соседние пиксели совпадают), бедная квантованная палитра
 * и «пустая середина» гистограммы градиентов — у аниме есть заливки и жёсткие
 * контуры, но почти нет полутоновых переходов, которыми полна живая съёмка
 * (текстуры, шум сенсора, плёночное зерно).
 *
 * Пороги подобраны по синтетике и первым прогонам — это эвристика v1,
 * калибровка на реальной библиотеке ещё предстоит.
 */
class FrameContentClassifier(
    private val sampler: FrameSampler,
    private val frameCount: Int = DEFAULT_FRAME_COUNT,
) : ContentClassifier {

    override fun classify(request: ClassificationRequest): Flow<ContentDetection> = flow {
        val frames = runCatching { sampler.sample(request.uri, frameCount) }
            .getOrDefault(emptyList())
        if (frames.isEmpty()) return@flow
        val detection = classifyFrames(frames)
        if (detection.contentClass != ContentClass.UNKNOWN) emit(detection)
    }

    fun classifyFrames(frames: List<FrameSample>): ContentDetection {
        val features = frames.map { FrameFeatures.extract(it) }
        val flat = features.map { it.flatRatio }.average().toFloat()
        val diversity = features.map { it.colorDiversity }.average().toFloat()
        val midGradient = features.map { it.midGradientRatio }.average().toFloat()
        val saturation = features.map { it.saturation }.average().toFloat()

        // Каждый признак → вклад 0..1, взвешенная сумма = «рисованность».
        val flatScore = ramp(flat, lo = 0.35f, hi = 0.75f)
        val paletteScore = 1f - ramp(diversity, lo = 0.02f, hi = 0.12f)
        val gradientScore = 1f - ramp(midGradient, lo = 0.08f, hi = 0.30f)
        val drawnScore = 0.45f * flatScore + 0.25f * paletteScore + 0.30f * gradientScore

        return when {
            drawnScore >= DRAWN_THRESHOLD -> {
                // Западная анимация обычно ещё проще и насыщеннее аниме;
                // граница условная, при сомнении выбираем ANIME (основной кейс).
                val cartoon = saturation > 0.55f && diversity < 0.02f
                ContentDetection(
                    contentClass = if (cartoon) ContentClass.CARTOON else ContentClass.ANIME,
                    confidence = confidence(drawnScore, from = DRAWN_THRESHOLD),
                    source = SOURCE,
                )
            }

            drawnScore <= LIVE_THRESHOLD -> ContentDetection(
                contentClass = ContentClass.LIVE_ACTION,
                confidence = confidence(1f - drawnScore, from = 1f - LIVE_THRESHOLD),
                source = SOURCE,
            )

            // Середина шкалы: сигналы противоречат друг другу, результат не эмитим —
            // остаётся действовать слой имени файла.
            else -> ContentDetection.UNKNOWN
        }
    }

    /** Линейный подъём 0→1 на отрезке [lo, hi]. */
    private fun ramp(value: Float, lo: Float, hi: Float): Float =
        ((value - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** Уверенность 0.6..0.85: чем дальше от порога, тем увереннее. */
    private fun confidence(score: Float, from: Float): Float =
        0.6f + 0.25f * ramp(score, lo = from, hi = 1f)

    private companion object {
        const val SOURCE = "frames"
        const val DEFAULT_FRAME_COUNT = 5
        const val DRAWN_THRESHOLD = 0.55f
        const val LIVE_THRESHOLD = 0.30f
    }
}

/** Статистика одного кадра; извлечение — один проход по строкам. */
internal class FrameFeatures(
    /** Доля горизонтальных пар соседей с почти совпадающим цветом. */
    val flatRatio: Float,
    /** Доля пар с полутоновым переходом (не заливка и не жёсткий контур). */
    val midGradientRatio: Float,
    /** Число различных цветов после квантования до 4 бит/канал, на пиксель. */
    val colorDiversity: Float,
    /** Средняя насыщенность (max-min по каналам, нормированная). */
    val saturation: Float,
) {
    companion object {
        private const val FLAT_DELTA = 24
        private const val EDGE_DELTA = 180

        fun extract(frame: FrameSample): FrameFeatures {
            val pixels = frame.argb
            val quantized = HashSet<Int>()
            var flat = 0
            var mid = 0
            var pairs = 0
            var saturationSum = 0f

            fun countPair(delta: Int) {
                when {
                    delta < FLAT_DELTA -> flat++
                    delta < EDGE_DELTA -> mid++
                }
                pairs++
            }

            for (y in 0 until frame.height) {
                val row = y * frame.width
                var prevR = 0
                var prevG = 0
                var prevB = 0
                for (x in 0 until frame.width) {
                    val p = pixels[row + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF

                    quantized.add(((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4))
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    if (max > 0) saturationSum += (max - min).toFloat() / max

                    if (x > 0) countPair(abs(r - prevR) + abs(g - prevG) + abs(b - prevB))
                    prevR = r
                    prevG = g
                    prevB = b
                }
            }

            val pixelCount = pixels.size.coerceAtLeast(1)
            val pairCount = pairs.coerceAtLeast(1)
            return FrameFeatures(
                flatRatio = flat.toFloat() / pairCount,
                midGradientRatio = mid.toFloat() / pairCount,
                colorDiversity = quantized.size.toFloat() / pixelCount,
                saturation = saturationSum / pixelCount,
            )
        }

        private fun abs(v: Int): Int = if (v < 0) -v else v
    }
}
