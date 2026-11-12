package com.rinwave.sakuro.core.upscale

import com.rinwave.sakuro.core.model.ContentClass
import com.rinwave.sakuro.core.model.UpscalePass
import com.rinwave.sakuro.core.model.UpscaleProfile

/**
 * Built-in presets for content classes (FEATURES.md §2.2).
 * On Media3 they are applied as ported GLSL ES passes, on libmpv as native `.glsl`.
 *
 * The anime presets are built on Anime4K Upscale CNN x2 S with no Sharpen pass
 * (no Restore CNN). On-device benchmarks (Pixel 9 Pro XL, two anime clips,
 * VMAF-NEG against the master) ranked plain Upscale S first: Restore before it
 * scored 6–8 points lower and mostly added oversharpening, and Upscale M was no
 * better than S at a higher cost.
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
        description = "2× CNN upscale with light denoise for SD anime and weak rips",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Denoise(0.35f),
            UpscalePass.Upscale(2f),
        ),
        builtIn = true,
    )

    val ANIME_HD = UpscaleProfile(
        id = "anime-hd",
        name = "Anime HD",
        description = "1.5× CNN upscale for 720p/1080p anime, no extra sharpening",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Upscale(1.5f),
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
