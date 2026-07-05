package com.rinwave.sakuro.engine.media3.anime4k

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * A single Anime4K [GlEffect] for Media3: hides the whole multi-pass render graph
 * of the mpv user-shaders behind one effect.
 *
 * [passes] — the already-parsed passes of the whole preset chain
 * (Clamp→Denoise→Restore→Upscale), concatenated in order from several `.glsl` files.
 * An empty list makes the effect a no-op.
 */
@UnstableApi
internal class Anime4KGlEffect(private val passes: List<UserShaderPass>) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4KShaderProgram(passes)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = passes.isEmpty()
}
