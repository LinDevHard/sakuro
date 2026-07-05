package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.math.roundToInt

/**
 * mpv render configuration for a preset: properties + a user-shader chain.
 * [shaders] — file names from assets/anime4k; into the `glsl-shaders` property
 * the engine substitutes absolute paths (see [MpvShaderStore]).
 */
internal data class MpvRenderConfig(
    val properties: List<Pair<String, String>>,
    val shaders: List<String>,
)

/**
 * Translates the abstract [UpscalePass] chain into an mpv configuration (ARCHITECTURE.md §4).
 *
 * For anime/animation the passes are translated into native Anime4K `.glsl`
 * user-shaders (including Denoise — the bundled ffmpeg has no denoise filters, and the shader
 * does not need libavfilter). The chain order is the canonical Anime4K one
 * (Clamp → Denoise → Restore → Upscale), not the preset's pass order.
 * The CNN size (S/M) is chosen by the pass strength.
 *
 * For other content Anime4K is not a fit by design — only mpv properties remain:
 * Upscale → a high-quality scaler (mpv always scales
 * to the surface size, so no factor is needed), Sharpen → `sharpen`, Denoise
 * degrades (the [UpscaleProfile] contract).
 *
 * Everything is applied on the fly, without re-prepare — unlike Media3.
 */
internal fun buildMpvRenderConfig(profile: UpscaleProfile): MpvRenderConfig {
    val shaders = buildAnime4kChain(profile)
    var scale = "bilinear"
    var sharpen = 0f
    for (pass in profile.passes) {
        when (pass) {
            is UpscalePass.Upscale -> scale = "ewa_lanczossharp"
            // Restore_CNN handles anime sharpening — the property would duplicate the effect.
            is UpscalePass.Sharpen -> if (shaders.isEmpty()) sharpen = pass.strength
            is UpscalePass.Denoise -> Unit
        }
    }
    return MpvRenderConfig(
        properties = listOf(
            "scale" to scale,
            "cscale" to scale,
            "sharpen" to sharpen.fmt(),
        ),
        shaders = shaders,
    )
}

private fun buildAnime4kChain(profile: UpscaleProfile): List<String> {
    val anime = profile.contentClass == ContentClass.ANIME || profile.contentClass == ContentClass.CARTOON
    if (!anime || profile.passes.isEmpty()) return emptyList()
    val chain = buildList {
        profile.passes.filterIsInstance<UpscalePass.Denoise>().firstOrNull()?.let {
            add("Anime4K_Denoise_Bilateral_Mode.glsl")
        }
        profile.passes.filterIsInstance<UpscalePass.Sharpen>().firstOrNull()?.let {
            add(if (it.strength >= HEAVY_SHARPEN) "Anime4K_Restore_CNN_M.glsl" else "Anime4K_Restore_CNN_S.glsl")
        }
        profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.let {
            add(if (it.factor >= HEAVY_UPSCALE) "Anime4K_Upscale_CNN_x2_M.glsl" else "Anime4K_Upscale_CNN_x2_S.glsl")
        }
    }
    if (chain.isEmpty()) return emptyList()
    // Highlight clamping protects the CNN passes from ringing on blown-out lines.
    return listOf("Anime4K_Clamp_Highlights.glsl") + chain
}

/** Sharpen-strength threshold at or above which the medium CNN model is used instead of the small one. */
private const val HEAVY_SHARPEN = 0.6f

/** Upscale-factor threshold at or above which the medium CNN model is used instead of the small one. */
private const val HEAVY_UPSCALE = 1.75f

/** Float → a string with 2 decimals and a dot separator regardless of locale. */
private fun Float.fmt(): String = ((this * 100).roundToInt() / 100.0).toString()
