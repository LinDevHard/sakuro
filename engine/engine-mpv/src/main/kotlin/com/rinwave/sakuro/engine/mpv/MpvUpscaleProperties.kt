package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.ParametricChain
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.math.roundToInt

/**
 * mpv render configuration for a preset: properties + a user-shader chain.
 * [shaders] — file names from assets/anime4k; into the `glsl-shaders` property
 * the engine substitutes absolute paths (see [MpvShaderStore]).
 * [userShaders] — chain file names (imported or bundled, see [MpvChainStore]);
 * when non-empty they replace [shaders] entirely. [userShaderParams] —
 * per-file `//!PARAM` overrides baked in by [MpvShaderMaterializer].
 */
internal data class MpvRenderConfig(
    val properties: List<Pair<String, String>>,
    val shaders: List<String>,
    val userShaders: List<String> = emptyList(),
    val userShaderParams: Map<String, Map<String, Float>> = emptyMap(),
)

/**
 * Translates the abstract [UpscalePass] chain into an mpv configuration (ARCHITECTURE.md §4).
 *
 * Selection order mirrors Media3 exactly:
 * 1. an explicit [UpscaleProfile.shaderChain] replaces everything;
 * 2. anime/cartoon passes translate into the canonical Anime4K `.glsl` chain
 *    (Clamp → Denoise → Restore → Upscale, CNN size by pass strength);
 * 3. otherwise the passes map through [ParametricChain] onto the same bundled
 *    shaders as on Media3, with slider strengths baked into their `//!PARAM`s.
 *
 * Properties: Upscale keeps `ewa_lanczossharp` for the final fit to the surface
 * (ravu is a ×2 prescaler). The `sharpen` property survives only for
 * [shadersAvailable] = false — the engine's degrade path when shader files
 * cannot be deployed; Denoise degrades there (the [UpscaleProfile] contract).
 *
 * Everything is applied on the fly, without re-prepare — unlike Media3.
 */
internal fun buildMpvRenderConfig(
    profile: UpscaleProfile,
    shadersAvailable: Boolean = true,
): MpvRenderConfig {
    // An explicit user chain replaces the engine's own shader selection.
    val explicit = if (shadersAvailable) profile.shaderChain else emptyList()
    val anime4k = if (shadersAvailable && explicit.isEmpty()) buildAnime4kChain(profile) else emptyList()
    val parametric = if (shadersAvailable && explicit.isEmpty() && anime4k.isEmpty()) {
        ParametricChain.forProfile(profile)
    } else {
        emptyList()
    }
    val userShaders = explicit.ifEmpty { parametric.map { it.shader.fileName } }

    var scale = "bilinear"
    var sharpen = 0f
    for (pass in profile.passes) {
        when (pass) {
            is UpscalePass.Upscale -> scale = "ewa_lanczossharp"
            // Shader chains own the look — the property would duplicate the effect.
            is UpscalePass.Sharpen -> if (anime4k.isEmpty() && userShaders.isEmpty()) sharpen = pass.strength
            is UpscalePass.Denoise -> Unit
        }
    }
    return MpvRenderConfig(
        properties = listOf(
            "scale" to scale,
            "cscale" to scale,
            "sharpen" to sharpen.fmt(),
        ),
        shaders = anime4k,
        userShaders = userShaders,
        // An explicit chain carries the user's own //!PARAM values; a parametric
        // chain derives them from the preset's pass strengths.
        userShaderParams = if (explicit.isNotEmpty()) {
            profile.shaderParams.filterKeys { it in explicit }
        } else {
            parametric.filter { it.params.isNotEmpty() }.associate { it.shader.fileName to it.params }
        },
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
