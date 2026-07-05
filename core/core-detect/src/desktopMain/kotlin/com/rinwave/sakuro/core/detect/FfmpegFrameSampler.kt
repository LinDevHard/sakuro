package com.rinwave.sakuro.core.detect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Desktop frame sampler via system ffmpeg/ffprobe (ARCHITECTURE.md §3.2:
 * desktop is a sandbox, no need to bundle a decoder just for detection). Best-effort:
 * no binaries in PATH, unreadable file, unparseable output — an empty list,
 * frame detection simply stays silent. The position grid matches the Android sampler:
 * evenly within 10..90% of the duration, past logos and credits at the edges.
 */
class FfmpegFrameSampler(
    private val ffmpegPath: String = "ffmpeg",
    private val ffprobePath: String = "ffprobe",
    private val targetSize: Int = TARGET_SIZE,
) : FrameSampler {

    override suspend fun sample(uri: String, maxFrames: Int): List<FrameSample> =
        withContext(Dispatchers.IO) {
            val file = toLocalFile(uri) ?: return@withContext emptyList()
            val durationSeconds = probeDuration(file) ?: return@withContext emptyList()
            val (width, height) = probeDimensions(file) ?: return@withContext emptyList()
            val (outWidth, outHeight) = scaledSize(width, height, targetSize)

            (1..maxFrames).mapNotNull { index ->
                val fraction = FRACTION_START + FRACTION_SPAN * index / (maxFrames + 1)
                grabFrame(file, durationSeconds * fraction, outWidth, outHeight)
            }
        }

    /** Detection works only with local files: a file:// URI or a direct path. */
    private fun toLocalFile(uri: String): File? {
        val file = when {
            uri.startsWith("file://") -> runCatching { File(URI(uri)) }.getOrNull()
            "://" in uri -> null
            else -> File(uri)
        }
        return file?.takeIf { it.isFile }
    }

    private fun probeDuration(file: File): Double? = runProcess(
        ffprobePath, "-v", "error",
        "-show_entries", "format=duration",
        "-of", "csv=p=0",
        file.absolutePath,
    )?.toString(Charsets.UTF_8)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }

    private fun probeDimensions(file: File): Pair<Int, Int>? {
        val output = runProcess(
            ffprobePath, "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=width,height",
            "-of", "csv=p=0",
            file.absolutePath,
        )?.toString(Charsets.UTF_8)?.trim() ?: return null
        return parseDimensions(output)
    }

    private fun grabFrame(file: File, atSeconds: Double, outWidth: Int, outHeight: Int): FrameSample? {
        val bytes = runProcess(
            ffmpegPath, "-v", "error",
            "-ss", formatSeconds(atSeconds),
            "-i", file.absolutePath,
            "-frames:v", "1",
            "-vf", "scale=$outWidth:$outHeight",
            "-f", "rawvideo",
            "-pix_fmt", "argb",
            "pipe:1",
        ) ?: return null
        return argbBytesToSample(bytes, outWidth, outHeight)
    }

    /** Process stdout, or null on any failure (missing binary, non-zero code, timeout). */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun runProcess(vararg command: String): ByteArray? {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.readBytes()
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            output.takeIf { process.exitValue() == 0 }
        } catch (ignored: Exception) {
            null
        }
    }

    internal companion object {
        const val TARGET_SIZE = 96
        const val FRACTION_START = 0.1
        const val FRACTION_SPAN = 0.8
        const val PROCESS_TIMEOUT_SECONDS = 15L
        private const val BYTES_PER_PIXEL = 4
        private const val BYTE_MASK = 0xFF

        // Locale-independent format: ffmpeg expects a dot in the seconds.
        fun formatSeconds(seconds: Double): String = "%.3f".format(java.util.Locale.ROOT, seconds)

        fun parseDimensions(csv: String): Pair<Int, Int>? {
            val parts = csv.lineSequence().firstOrNull()?.split(',') ?: return null
            val width = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val height = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            return width to height
        }

        /** Fits the frame into [target] by the longer side, never upscales. */
        fun scaledSize(width: Int, height: Int, target: Int): Pair<Int, Int> {
            val scale = target.toDouble() / maxOf(width, height)
            if (scale >= 1.0) return width to height
            return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
        }

        /** Raw ARGB bytes from ffmpeg (one byte per channel, A first) → FrameSample. */
        fun argbBytesToSample(bytes: ByteArray, width: Int, height: Int): FrameSample? {
            if (bytes.size != width * height * BYTES_PER_PIXEL) return null
            val pixels = IntArray(width * height) { pixel ->
                val offset = pixel * BYTES_PER_PIXEL
                (bytes[offset].toInt() and BYTE_MASK shl 24) or
                    (bytes[offset + 1].toInt() and BYTE_MASK shl 16) or
                    (bytes[offset + 2].toInt() and BYTE_MASK shl 8) or
                    (bytes[offset + 3].toInt() and BYTE_MASK)
            }
            return FrameSample(width, height, pixels)
        }
    }
}
