package com.rinwave.sakuro.core.detect

/**
 * A downscaled video frame for analysis. Pixels are ARGB-8888 row by row,
 * ~100 px on the longer side: enough for color and gradient statistics,
 * while decoding stays cheap.
 */
class FrameSample(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(argb.size == width * height) {
            "argb size ${argb.size} != $width x $height"
        }
    }
}

/**
 * Pulls frame samples from the video independently of the playback engine
 * (Android — MediaMetadataRetriever, desktop — no implementation yet).
 * Frames are taken evenly across the duration, skipping the first/last percent,
 * where logos and credits usually are.
 */
interface FrameSampler {

    /** Empty list = sampling unavailable (frame-based detection stays silent). */
    suspend fun sample(uri: String, maxFrames: Int): List<FrameSample>
}
