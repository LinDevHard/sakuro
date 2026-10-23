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
import com.rinwave.sakuro.engine.media3.usershader.ShaderDocument
import com.rinwave.sakuro.engine.media3.usershader.UserShaderGlEffect
import kotlin.math.roundToInt

/**
 * Builds a `GlEffect` chain from the abstract [UpscaleProfile] (ARCHITECTURE.md §4):
 * - an explicit [UpscaleProfile.shaderChain] → one [UserShaderGlEffect] with the
 *   merged custom chain;
 * - anime/cartoon → a single [UserShaderGlEffect] with the canonical multi-pass
 *   Anime4K CNN chain followed by presentation at the requested size;
 * - otherwise the passes map through [ParametricChain] onto bundled shaders
 *   (denoise → Sakuro bilateral, upscale → ravu-r3 ×2, sharpen → CAS), one
 *   effect per pass so the canonical order survives and a broken pass degrades
 *   alone; strengths feed the shaders' `//!PARAM`s. A [Presentation] trims the
 *   result to the capped Upscale target, including runtime shader fallback.
 */
@UnstableApi
object UpscaleEffectChain {

    const val MAX_TARGET_HEIGHT = 2160

    internal data class BuiltChain(
        val effects: List<Effect>,
        val telemetry: EffectChainTelemetry,
        val sourceDependent: Boolean = false,
    )

    internal fun build(context: Context, profile: UpscaleProfile, sourceHeight: Int): BuiltChain {
        val custom = UserShaderChain.load(context, profile)
        val anime = if (custom?.passes?.isNotEmpty() == true) {
            ShaderDocument.EMPTY
        } else {
            Anime4KChain.load(context, profile)
        }
        return build(profile, sourceHeight, custom, anime) { UserShaderChain.loadByName(context, it) }
    }

    /** Assembles already loaded documents; file I/O stays outside chain construction. */
    internal fun build(
        profile: UpscaleProfile,
        sourceHeight: Int,
        custom: ShaderDocument?,
        anime: ShaderDocument,
        loadShader: (String) -> ShaderDocument?,
    ): BuiltChain {
        val telemetry = EffectChainTelemetry()
        if (custom != null && custom.passes.isNotEmpty()) {
            val label = profile.shaderChain.joinToString(" → ")
            return BuiltChain(
                listOf(UserShaderGlEffect(custom, onConfigured = telemetry.addShader(label))),
                telemetry,
            )
        }
        if (anime.passes.isNotEmpty()) {
            val effects = buildList {
                add(UserShaderGlEffect(anime, onConfigured = telemetry.addShader("Anime4K")))
                addPresentation(profile, sourceHeight, telemetry)
            }
            return BuiltChain(effects, telemetry, profile.passes.any { it is UpscalePass.Upscale })
        }
        return BuiltChain(
            buildParametricChain(profile, sourceHeight, telemetry, loadShader), telemetry,
            ParametricChain.forProfile(profile).any { it.shader.role == BundledShaderRole.UPSCALE },
        )
    }

    private fun buildParametricChain(
        profile: UpscaleProfile,
        sourceHeight: Int,
        telemetry: EffectChainTelemetry,
        loadShader: (String) -> ShaderDocument?,
    ): List<Effect> = buildList {
        ParametricChain.forProfile(profile).forEach { link ->
            val document = loadShader(link.shader.fileName)
            val loaded = document != null && document.passes.isNotEmpty()
            if (loaded) {
                add(UserShaderGlEffect(document!!, link.params, telemetry.addShader(link.shader.displayName)))
            }
            if (link.shader.role == BundledShaderRole.UPSCALE) {
                addPresentation(profile, sourceHeight, telemetry)
            }
        }
    }

    private fun MutableList<Effect>.addPresentation(
        profile: UpscaleProfile,
        sourceHeight: Int,
        telemetry: EffectChainTelemetry,
    ) {
        if (sourceHeight <= 0 || profile.passes.none { it is UpscalePass.Upscale }) return
        val target = targetHeight(profile, sourceHeight)
        // Always normalize: a successfully loaded shader may still fail on the GL thread.
        add(Presentation.createForHeight(target))
        telemetry.addPresentation(target)
    }

    /** Target frame height after the chain — for the debug overlay. */
    fun targetHeight(profile: UpscaleProfile, sourceHeight: Int): Int {
        val factor = profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.factor ?: return sourceHeight
        return (sourceHeight * factor).roundToInt().coerceIn(1, MAX_TARGET_HEIGHT)
    }
}
