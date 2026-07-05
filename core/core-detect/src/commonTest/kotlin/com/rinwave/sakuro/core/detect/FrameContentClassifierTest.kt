package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameContentClassifierTest {

    private val classifier = FrameContentClassifier(EmptySampler)

    @Test
    fun `flat fills with contours - anime`() {
        val frames = List(3) { stripedFrame(mutedPalette) }
        val detection = classifier.classifyFrames(frames)

        assertEquals(ContentClass.ANIME, detection.contentClass)
        assertTrue(detection.confidence >= 0.6f, "confidence=${detection.confidence}")
        assertEquals("frames", detection.source)
    }

    @Test
    fun `oversaturated fills from a couple of colors - cartoon`() {
        val frames = List(3) { stripedFrame(vividPalette) }
        val detection = classifier.classifyFrames(frames)

        assertEquals(ContentClass.CARTOON, detection.contentClass)
    }

    @Test
    fun `noisy texture with a rich palette - live footage`() {
        val frames = List(3) { index -> noisyFrame(seed = index) }
        val detection = classifier.classifyFrames(frames)

        assertEquals(ContentClass.LIVE_ACTION, detection.contentClass)
        assertTrue(detection.confidence >= 0.6f, "confidence=${detection.confidence}")
    }

    @Test
    fun `empty sampler - stream with no results`() = runTest {
        val results = classifier
            .classify(ClassificationRequest(uri = "file:///video.mp4", title = "video"))
            .toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `sampler error does not break the stream`() = runTest {
        val failing = FrameContentClassifier(
            object : FrameSampler {
                override suspend fun sample(uri: String, maxFrames: Int): List<FrameSample> =
                    error("no codec")
            },
        )

        val results = failing
            .classify(ClassificationRequest(uri = "file:///video.mp4", title = "video"))
            .toList()

        assertTrue(results.isEmpty())
    }

    // --- Synthetic frames ----------------------------------------------------

    private object EmptySampler : FrameSampler {
        override suspend fun sample(uri: String, maxFrames: Int): List<FrameSample> = emptyList()
    }

    /** Muted colors: a drawn frame, but not an "acid" cartoon. */
    private val mutedPalette = intArrayOf(
        rgb(120, 120, 140),
        rgb(200, 190, 180),
        rgb(80, 90, 100),
        rgb(150, 140, 120),
    )

    /** Clean saturated colors of Western animation. */
    private val vividPalette = intArrayOf(
        rgb(255, 0, 0),
        rgb(0, 200, 0),
        rgb(0, 0, 255),
        rgb(255, 220, 0),
    )

    /** Vertical solid-color stripes: fills + hard contours. */
    private fun stripedFrame(palette: IntArray, size: Int = 96): FrameSample {
        val argb = IntArray(size * size)
        val stripe = size / palette.size
        for (y in 0 until size) {
            for (x in 0 until size) {
                argb[y * size + x] = palette[(x / stripe).coerceAtMost(palette.size - 1)]
            }
        }
        return FrameSample(size, size, argb)
    }

    /** Pseudo-random texture: wide palette, a transition at every pixel. */
    private fun noisyFrame(seed: Int, size: Int = 96): FrameSample {
        val argb = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (x * 37 + y * 17 + seed * 5) % 199 + 40
                val g = (x * 53 + y * 29 + seed * 7) % 211 + 30
                val b = (x * 71 + y * 13 + seed * 11) % 191 + 50
                argb[y * size + x] = rgb(r, g, b)
            }
        }
        return FrameSample(size, size, argb)
    }
}

internal fun rgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
