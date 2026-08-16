package com.rinwave.sakuro.core.upscale

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A video-processing preset (FEATURES.md §2): an abstract chain of passes
 * that each engine applies with the means available to it
 * (libmpv → a `.glsl` chain, Media3 → a `GlEffect` chain).
 * Passes incompatible with an engine degrade (are skipped).
 */
@Serializable
data class UpscaleProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val contentClass: ContentClass = ContentClass.UNKNOWN,
    val passes: List<UpscalePass> = emptyList(),
    /** Built-in presets cannot be deleted/overwritten. */
    val builtIn: Boolean = false,
    /**
     * An explicit mpv user-shader chain: ordered file names, each an imported
     * shader or a vendored [BundledShaders] entry (an import shadows a bundled
     * name). When non-empty it replaces the engine's own shader selection;
     * [passes] still drive the scaler properties and the adaptive controller.
     */
    val shaderChain: List<String> = emptyList(),
    /**
     * `//!PARAM` overrides for the chain, by shader file name then param name.
     * Only values the user changed are stored; everything else runs at the
     * shader's own default (see [ShaderTunable]).
     */
    val shaderParams: Map<String, Map<String, Float>> = emptyMap(),
) {
    val isEnabled: Boolean get() = passes.isNotEmpty() || shaderChain.isNotEmpty()
}

@Serializable
sealed interface UpscalePass {

    /** Scaling to the target resolution: a multiplier on the source height. */
    @Serializable
    @SerialName("upscale")
    data class Upscale(val factor: Float) : UpscalePass

    /** Sharpening with anti-ringing, strength 0..1. */
    @Serializable
    @SerialName("sharpen")
    data class Sharpen(val strength: Float) : UpscalePass

    /** Light edge-preserving denoise, strength 0..1. */
    @Serializable
    @SerialName("denoise")
    data class Denoise(val strength: Float) : UpscalePass
}

fun UpscalePass.describe(): String = when (this) {
    is UpscalePass.Upscale -> "upscale ×$factor"
    is UpscalePass.Sharpen -> "sharpen $strength"
    is UpscalePass.Denoise -> "denoise $strength"
}
