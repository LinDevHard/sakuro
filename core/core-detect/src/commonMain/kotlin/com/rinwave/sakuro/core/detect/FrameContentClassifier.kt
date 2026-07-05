package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The second detection layer (FEATURES.md §1): statistics over frame samples.
 * Three downscale-robust features tell drawn content from live footage:
 * flat fills (neighboring pixels match), a poor quantized palette,
 * and an "empty middle" of the gradient histogram — anime has fills and hard
 * contours but almost no midtone transitions, which live footage is full of
 * (textures, sensor noise, film grain).
 *
 * Thresholds are tuned on synthetics and first runs — this is a v1 heuristic;
 * calibration on a real library is still to come.
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

        // Each feature → a 0..1 contribution, the weighted sum = "drawn-ness".
        val flatScore = ramp(flat, lo = 0.35f, hi = 0.75f)
        val paletteScore = 1f - ramp(diversity, lo = 0.02f, hi = 0.12f)
        val gradientScore = 1f - ramp(midGradient, lo = 0.08f, hi = 0.30f)
        val drawnScore = 0.45f * flatScore + 0.25f * paletteScore + 0.30f * gradientScore

        return when {
            drawnScore >= DRAWN_THRESHOLD -> {
                // Western animation is usually even simpler and more saturated than anime;
                // the boundary is fuzzy; when in doubt we pick ANIME (the main case).
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

            // Middle of the scale: signals contradict each other, we do not emit —
            // the file-name layer is left to act.
            else -> ContentDetection.UNKNOWN
        }
    }

    /** Linear ramp 0→1 over the interval [lo, hi]. */
    private fun ramp(value: Float, lo: Float, hi: Float): Float =
        ((value - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** Confidence 0.6..0.85: the farther from the threshold, the more confident. */
    private fun confidence(score: Float, from: Float): Float =
        0.6f + 0.25f * ramp(score, lo = from, hi = 1f)

    private companion object {
        const val SOURCE = "frames"
        const val DEFAULT_FRAME_COUNT = 5
        const val DRAWN_THRESHOLD = 0.55f
        const val LIVE_THRESHOLD = 0.30f
    }
}

/** Per-frame statistics; extraction is a single pass over the rows. */
internal class FrameFeatures(
    /** Fraction of horizontal neighbor pairs with near-identical color. */
    val flatRatio: Float,
    /** Fraction of pairs with a midtone transition (neither a fill nor a hard edge). */
    val midGradientRatio: Float,
    /** Number of distinct colors after quantization to 4 bits/channel, per pixel. */
    val colorDiversity: Float,
    /** Mean saturation (max-min across channels, normalized). */
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
