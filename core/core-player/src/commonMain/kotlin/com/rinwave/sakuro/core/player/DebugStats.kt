package com.rinwave.sakuro.core.player

/** Data for the "stats for nerds" debug overlay (FEATURES.md §4.1). */
data class DebugStats(
    val engine: String = "",
    val videoCodec: String? = null,
    val videoDecoder: String? = null,
    val sourceResolution: String? = null,
    val outputResolution: String? = null,
    val videoFps: Float? = null,
    val droppedFrames: Int = 0,
    val renderedFrames: Int? = null,
    /**
     * Frames per second the engine is actually putting on screen right now, as
     * reported by the engine itself (Media3: rendered-buffer delta, mpv:
     * `estimated-vf-fps`). Null until the engine has enough samples.
     */
    val renderFps: Float? = null,
    val bitrateKbps: Int? = null,
    val colorInfo: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioSampleRateHz: Int? = null,
    val upscaleProfile: String? = null,
    val upscalePasses: List<String> = emptyList(),
    /** Free-form engine pairs (container, surface, etc.). */
    val extras: Map<String, String> = emptyMap(),
)
