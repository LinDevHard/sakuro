package com.rinwave.sakuro.core.upscale

import com.rinwave.sakuro.core.model.DisplayLimitStatus
import com.rinwave.sakuro.core.model.DisplaySize
import com.rinwave.sakuro.core.model.UpscalePass
import com.rinwave.sakuro.core.model.UpscaleProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Caps upscaling at what the panel can actually show: rendering a 1080p video
 * at 2160p for a 1080p screen only costs GPU time, the compositor scales it
 * back down. Everything except the upscale step (denoise, restore, sharpen)
 * is untouched.
 */
object DisplayUpscaleLimit {

    /**
     * Below this the upscale step is dropped. Matches the Anime4K upscaler's own
     * `//!WHEN` gate (output must be > 1.2× the input), so Sakuro and the shader
     * agree on when an upscale is worth running.
     */
    const val MIN_USEFUL_FACTOR = 1.2f

    data class Result(
        /** The profile to build; equals the input when nothing was limited. */
        val profile: UpscaleProfile,
        val status: DisplayLimitStatus?,
        /** Video size at fit-to-screen — the real `OUTPUT` for explicit shader chains. */
        val screenWidth: Int,
        val screenHeight: Int,
    )

    /**
     * How much a [sourceWidth]×[sourceHeight] video can grow on [display] at
     * fit-to-screen, in whichever orientation shows it bigger (the player
     * rotates freely, so the limit should not flip with the phone).
     */
    fun fitScale(sourceWidth: Int, sourceHeight: Int, display: DisplaySize): Float {
        val long = max(display.width, display.height).toFloat()
        val short = min(display.width, display.height).toFloat()
        val landscape = min(long / sourceWidth, short / sourceHeight)
        val portrait = min(short / sourceWidth, long / sourceHeight)
        return max(landscape, portrait)
    }

    /** [display] null means no limit (the setting is off or the panel is unknown). */
    fun apply(profile: UpscaleProfile, sourceWidth: Int, sourceHeight: Int, display: DisplaySize?): Result? {
        if (display == null || sourceWidth <= 0 || sourceHeight <= 0) return null
        if (display.width <= 0 || display.height <= 0) return null
        val fit = fitScale(sourceWidth, sourceHeight, display)
        val screenWidth = (sourceWidth * fit).roundToInt()
        val screenHeight = (sourceHeight * fit).roundToInt()

        if (profile.shaderChain.isNotEmpty()) {
            // The chain's own //!WHEN gates decide against the real screen size;
            // say so only when they will most likely switch the upscaler off.
            val status = if (fit < MIN_USEFUL_FACTOR) {
                DisplayLimitStatus(DisplayLimitStatus.Kind.SKIPPED, null, 1f, screenHeight)
            } else {
                null
            }
            return Result(profile, status, screenWidth, screenHeight)
        }

        val upscale = profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()
        if (upscale == null || upscale.factor <= fit) {
            return Result(profile, null, screenWidth, screenHeight)
        }
        return if (fit < MIN_USEFUL_FACTOR) {
            Result(
                profile = profile.copy(passes = profile.passes - upscale),
                status = DisplayLimitStatus(DisplayLimitStatus.Kind.SKIPPED, upscale.factor, 1f, screenHeight),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        } else {
            val factor = (fit * FACTOR_PRECISION).toInt() / FACTOR_PRECISION
            Result(
                profile = profile.copy(
                    passes = profile.passes.map { if (it === upscale) UpscalePass.Upscale(factor) else it },
                ),
                status = DisplayLimitStatus(DisplayLimitStatus.Kind.LIMITED, upscale.factor, factor, screenHeight),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        }
    }

    /** Factors are rounded down to 0.01 so the frame never exceeds the panel. */
    private const val FACTOR_PRECISION = 100f
}
