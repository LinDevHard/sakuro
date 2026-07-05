package com.rinwave.sakuro.core.detect

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Frame sampler via MediaMetadataRetriever: opens the file separately
 * from the playing engine, so it does not disturb playback. Frames are taken
 * evenly from the middle of the clip (10..90% of duration) — the edges more often
 * hold logos, black inserts and credits.
 */
class RetrieverFrameSampler(
    private val context: Context,
    private val targetSize: Int = TARGET_SIZE,
) : FrameSampler {

    // Retriever throws various RuntimeExceptions on broken files — sampling is best-effort,
    // any error means "no frames", classification continues via the other layers.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun sample(uri: String, maxFrames: Int): List<FrameSample> =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?: return@withContext emptyList()

                (1..maxFrames).mapNotNull { index ->
                    // Even grid within 10..90% of the duration.
                    val fraction = 0.1f + 0.8f * index / (maxFrames + 1)
                    val timeUs = (durationMs * 1000 * fraction).toLong()
                    grabFrame(retriever, timeUs)?.let(::toSample)
                }
            } catch (ignored: Exception) {
                emptyList()
            } finally {
                retriever.release()
            }
        }

    private fun grabFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetSize,
                targetSize,
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { full ->
                    val scale = targetSize.toFloat() / maxOf(full.width, full.height)
                    if (scale >= 1f) return@let full
                    val scaled = Bitmap.createScaledBitmap(
                        full,
                        (full.width * scale).toInt().coerceAtLeast(1),
                        (full.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                    full.recycle()
                    scaled
                }
        }

    private fun toSample(bitmap: Bitmap): FrameSample {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sample = FrameSample(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()
        return sample
    }

    private companion object {
        const val TARGET_SIZE = 96
    }
}
