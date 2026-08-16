package com.rinwave.sakuro.engine.media3

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import com.rinwave.sakuro.core.upscale.BundledShaderRole
import com.rinwave.sakuro.core.upscale.ParametricChain
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.engine.media3.anime4k.Anime4KChain
import com.rinwave.sakuro.engine.media3.usershader.UserShaderGlEffect
import kotlin.math.roundToInt

/**
 * Builds a `GlEffect` chain from the abstract [UpscaleProfile] (ARCHITECTURE.md §4):
 * - an explicit [UpscaleProfile.shaderChain] → one [UserShaderGlEffect] with the
 *   merged custom chain;
 * - anime/cartoon → a single [UserShaderGlEffect] with the canonical multi-pass
 *   Anime4K CNN chain (depth-to-space already gives ×2);
 * - otherwise the passes map through [ParametricChain] onto bundled shaders
 *   (denoise → Sakuro bilateral, upscale → ravu-r3 ×2, sharpen → CAS), one
 *   effect per pass so the canonical order survives and a broken pass degrades
 *   alone; strengths feed the shaders' `//!PARAM`s. A [Presentation] trims the
 *   ravu ×2 result to the exact Upscale factor when they differ.
 */
@UnstableApi
object UpscaleEffectChain {

    const val MAX_TARGET_HEIGHT = 2160

    fun build(context: Context, profile: UpscaleProfile, sourceHeight: Int): List<Effect> {
        // An explicit user-shader chain overrides the engine's own selection.
        UserShaderChain.load(context, profile)?.let { custom ->
            // The runtime resolves params against the merged document, so the
            // per-file overrides collapse into one map; a name declared by two
            // shaders of the chain takes the value of the later one.
            if (custom.passes.isNotEmpty()) {
                val params = profile.shaderChain.fold(emptyMap<String, Float>()) { acc, file ->
                    acc + profile.shaderParams[file].orEmpty()
                }
                return listOf(UserShaderGlEffect(custom, params))
            }
        }
        val anime4kChain = Anime4KChain.load(context, profile)
        if (anime4kChain.passes.isNotEmpty()) return listOf(UserShaderGlEffect(anime4kChain))
        return buildParametricChain(context, profile, sourceHeight)
    }

    private fun buildParametricChain(
        context: Context,
        profile: UpscaleProfile,
        sourceHeight: Int,
    ): List<Effect> = buildList {
        ParametricChain.forProfile(profile).forEach { link ->
            val document = UserShaderChain.loadByName(context, link.shader.fileName)
            val loaded = document != null && document.passes.isNotEmpty()
            if (loaded) add(UserShaderGlEffect(document!!, link.params))
            if (link.shader.role == BundledShaderRole.UPSCALE) {
                presentationFor(profile, sourceHeight, shaderDoubles = loaded)?.let { add(it) }
            }
        }
    }

    /**
     * Trims/extends the frame to the exact Upscale target. With ravu loaded the
     * frame is already ×2 — a presentation is added only when the factor differs;
     * without it the presentation carries the whole upscale (the old behavior).
     */
    private fun presentationFor(
        profile: UpscaleProfile,
        sourceHeight: Int,
        shaderDoubles: Boolean,
    ): Presentation? {
        if (sourceHeight <= 0) return null
        val factor = profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.factor ?: return null
        val target = (sourceHeight * factor).roundToInt().coerceAtMost(MAX_TARGET_HEIGHT)
        val current = if (shaderDoubles) sourceHeight * 2 else sourceHeight
        if (target <= sourceHeight || target == current) return null
        return Presentation.createForHeight(target)
    }

    /** Target frame height after the chain — for the debug overlay. */
    fun targetHeight(profile: UpscaleProfile, sourceHeight: Int): Int {
        var height = sourceHeight
        profile.passes.filterIsInstance<UpscalePass.Upscale>().forEach { pass ->
            height = (height * pass.factor).roundToInt().coerceAtMost(MAX_TARGET_HEIGHT)
        }
        return height
    }
}
