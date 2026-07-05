package com.rinwave.sakuro.core.detect

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfmpegFrameSamplerTest {

    // --- pure parsing/conversion functions ---

    @Test
    fun `parseDimensions reads csv pair`() {
        assertEquals(854 to 480, FfmpegFrameSampler.parseDimensions("854,480"))
        assertEquals(1920 to 1080, FfmpegFrameSampler.parseDimensions("1920,1080\n"))
    }

    @Test
    fun `parseDimensions rejects garbage`() {
        assertNull(FfmpegFrameSampler.parseDimensions(""))
        assertNull(FfmpegFrameSampler.parseDimensions("N/A,480"))
        assertNull(FfmpegFrameSampler.parseDimensions("0,480"))
        assertNull(FfmpegFrameSampler.parseDimensions("854"))
    }

    @Test
    fun `scaledSize fits larger side and never upscales`() {
        assertEquals(96 to 54, FfmpegFrameSampler.scaledSize(1920, 1080, 96))
        assertEquals(54 to 96, FfmpegFrameSampler.scaledSize(1080, 1920, 96))
        assertEquals(64 to 48, FfmpegFrameSampler.scaledSize(64, 48, 96))
    }

    @Test
    fun `formatSeconds is locale independent`() {
        assertEquals("12.345", FfmpegFrameSampler.formatSeconds(12.3451))
    }

    @Test
    fun `argbBytesToSample decodes channels`() {
        // One pixel: A=0xFF, R=0x11, G=0x22, B=0x33.
        val bytes = byteArrayOf(0xFF.toByte(), 0x11, 0x22, 0x33)
        val sample = FfmpegFrameSampler.argbBytesToSample(bytes, 1, 1)
        assertEquals(0xFF112233.toInt(), sample?.argb?.single())
    }

    @Test
    fun `argbBytesToSample rejects size mismatch`() {
        assertNull(FfmpegFrameSampler.argbBytesToSample(ByteArray(3), 1, 1))
    }

    // --- integration with real ffmpeg (skipped if the binaries are not in PATH) ---

    @Test
    fun `samples frames from a real video when ffmpeg is available`() = runTest {
        if (!ffmpegAvailable()) return@runTest

        val video = File.createTempFile("sakuro-sampler", ".mp4").apply { deleteOnExit() }
        val generate = ProcessBuilder(
            "ffmpeg", "-v", "error", "-y",
            "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=10",
            video.absolutePath,
        ).start()
        assertTrue(generate.waitFor() == 0, "ffmpeg could not generate the test clip")

        val samples = FfmpegFrameSampler().sample(video.absolutePath, maxFrames = 3)

        assertEquals(3, samples.size)
        samples.forEach { sample ->
            assertEquals(96, sample.width)
            assertEquals(72, sample.height)
            // testsrc is colorful: the frame must not be monotone.
            assertTrue(sample.argb.distinct().size > 1)
        }
    }

    @Test
    fun `returns empty for non-local uris and missing files`() = runTest {
        val sampler = FfmpegFrameSampler()
        assertEquals(emptyList(), sampler.sample("fake://sample-1", maxFrames = 3))
        assertEquals(emptyList(), sampler.sample("/nonexistent/path.mp4", maxFrames = 3))
    }

    private fun ffmpegAvailable(): Boolean = listOf("ffmpeg", "ffprobe").all { binary ->
        runCatching {
            ProcessBuilder(binary, "-version").start().waitFor() == 0
        }.getOrDefault(false)
    }
}
