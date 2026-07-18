package com.rinwave.sakuro.core.upscale

/** One resolved element of a parametric chain: a bundled shader and its `//!PARAM` values. */
data class ParametricLink(
    val shader: BundledShader,
    val params: Map<String, Float> = emptyMap(),
)

/**
 * Translates the abstract [UpscalePass] chain into bundled shaders with real
 * tunables — the same table for both engines, so a preset's sliders produce
 * the same look everywhere:
 * - Denoise → the Sakuro bilateral (`intensity` = strength);
 * - Upscale → ravu-r3 ×2 (the fragment variant — runs on any ES 3.0 device;
 *   ravu-lite stays available through explicit chains);
 * - Sharpen → AMD FidelityFX CAS (`SHARPENING` = strength).
 *
 * The canonical order is Denoise → Upscale → Sharpen (sharpening runs after
 * scaling), regardless of the preset's pass order — as with Anime4K.
 *
 * Engines consult this only after their own priority branches: an explicit
 * [UpscaleProfile.shaderChain] and the canonical Anime4K translation for
 * anime/cartoon content both take precedence.
 */
object ParametricChain {

    fun forProfile(profile: UpscaleProfile): List<ParametricLink> {
        if (profile.shaderChain.isNotEmpty()) return emptyList()
        return buildList {
            profile.passes.filterIsInstance<UpscalePass.Denoise>().firstOrNull()?.let { pass ->
                add(link("sakuro-denoise", "intensity" to pass.strength.coerceIn(0f, 1f)))
            }
            profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.let {
                add(link("ravu-r3"))
            }
            profile.passes.filterIsInstance<UpscalePass.Sharpen>().firstOrNull()?.let { pass ->
                add(link("cas", "SHARPENING" to pass.strength.coerceIn(0f, 1f)))
            }
        }
    }

    private fun link(id: String, vararg params: Pair<String, Float>): ParametricLink =
        ParametricLink(checkNotNull(BundledShaders.byId(id)) { "unknown bundled shader '$id'" }, params.toMap())
}
