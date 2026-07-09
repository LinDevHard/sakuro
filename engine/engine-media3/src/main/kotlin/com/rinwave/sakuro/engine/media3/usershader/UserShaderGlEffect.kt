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
 */
@UnstableApi
internal class UserShaderGlEffect(private val document: ShaderDocument) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        UserShaderProgram(document)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = document.passes.isEmpty()
}
