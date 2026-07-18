package com.rinwave.sakuro.engine.media3.usershader

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * A single [GlEffect] for Media3 that hides a whole multi-pass mpv user-shader
 * render graph behind one effect.
 *
 * [document] — the already-parsed chain (possibly several `.glsl` files merged
 * in order, e.g. Clamp→Denoise→Restore→Upscale for Anime4K). An empty document
 * makes the effect a no-op.
 *
 * [paramValues] — `//!PARAM` overrides by name, fixed for the effect's lifetime
 * (baked into the generated GLSL); changing a value means building a new effect.
 */
@UnstableApi
internal class UserShaderGlEffect(
    private val document: ShaderDocument,
    private val paramValues: Map<String, Float> = emptyMap(),
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        UserShaderProgram(document, paramValues = paramValues)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = document.passes.isEmpty()
}
