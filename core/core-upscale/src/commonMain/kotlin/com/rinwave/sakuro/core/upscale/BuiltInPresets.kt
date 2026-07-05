package com.rinwave.sakuro.core.upscale

/**
 * Built-in presets for content classes (FEATURES.md §2.2).
 * The chains are inspired by Anime4K modes A/B/C; on Media3 they are applied
 * as ported GLSL ES passes, on libmpv as native `.glsl`.
 */
object BuiltInPresets {

    val OFF = UpscaleProfile(
        id = "off",
        name = "Off",
        description = "The original picture with no processing",
        builtIn = true,
    )

    val ANIME_SD = UpscaleProfile(
        id = "anime-sd",
        name = "Anime SD",
        description = "2× upscale, denoise and aggressive sharpening for SD anime and weak rips",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Denoise(0.35f),
            UpscalePass.Upscale(2f),
            UpscalePass.Sharpen(0.8f),
        ),
        builtIn = true,
    )

    val ANIME_HD = UpscaleProfile(
        id = "anime-hd",
        name = "Anime HD",
        description = "1.5× upscale and moderate sharpening for 720p/1080p anime",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Upscale(1.5f),
            UpscalePass.Sharpen(0.5f),
        ),
        builtIn = true,
    )

    val LIVE_ACTION_LIGHT = UpscaleProfile(
        id = "live-light",
        name = "Live-action light",
        description = "Gentle sharpening without upscaling — for live-action footage",
        contentClass = ContentClass.LIVE_ACTION,
        passes = listOf(
            UpscalePass.Sharpen(0.25f),
        ),
        builtIn = true,
    )

    val all: List<UpscaleProfile> = listOf(OFF, ANIME_SD, ANIME_HD, LIVE_ACTION_LIGHT)

    fun byId(id: String?): UpscaleProfile? = all.firstOrNull { it.id == id }

    /** Automatic preset selection by content class (for core-detect, Phase 2). */
    fun forContentClass(contentClass: ContentClass): UpscaleProfile = when (contentClass) {
        ContentClass.ANIME -> ANIME_HD
        ContentClass.CARTOON -> ANIME_HD
        ContentClass.LIVE_ACTION -> LIVE_ACTION_LIGHT
        ContentClass.UNKNOWN -> OFF
    }
}
