package com.rinwave.media3.usershader

import android.content.Context
import androidx.media3.common.util.Size
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
 *
 * [targetSize] — the size the video is finally shown at (mpv's target/display
 * rect). It is the `OUTPUT` pseudo-texture for `//!WHEN`/`//!WIDTH` expressions
 * and the `target_size` uniform, so upscalers gated on "output larger than
 * the source" skip themselves when the screen is not bigger. Null means
 * unknown: `OUTPUT` is then assumed 4× the input, which keeps every upscale
 * gate open. Like the params, it is fixed for the effect's lifetime.
 *
 * [onConfigured] runs on the Media3 GL thread each time the graph is (re)built.
 */
@UnstableApi
public class UserShaderGlEffect(
    private val document: ShaderDocument,
    private val paramValues: Map<String, Float> = emptyMap(),
    private val targetSize: Size? = null,
    private val onConfigured: (ShaderGraphRuntimeStats) -> Unit = {},
) : GlEffect {

    init {
        require(targetSize == null || (targetSize.width > 0 && targetSize.height > 0)) {
            "targetSize must be positive, was $targetSize"
        }
    }

    public override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        UserShaderProgram(document, targetSize = targetSize, paramValues = paramValues, onConfigured = onConfigured)

    public override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = document.isEmpty
}
