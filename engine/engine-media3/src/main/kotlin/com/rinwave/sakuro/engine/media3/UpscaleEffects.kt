package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.engine.media3.anime4k.Anime4KChain
import com.rinwave.sakuro.engine.media3.usershader.UserShaderGlEffect
import kotlin.math.roundToInt

/**
 * Builds a `GlEffect` chain from the abstract [UpscaleProfile] (ARCHITECTURE.md §4):
 * - anime/cartoon → a single [UserShaderGlEffect] with a real multi-pass Anime4K CNN;
 *   depth-to-space already gives ×2, so a separate
 *   [Presentation] is not needed;
 * - Upscale → [Presentation] with the target height (a real change of output resolution);
 * - Sharpen → a GLSL ES port of an Anime4K-style "clamp highlights + sharpen" pass;
 * - Denoise → a light edge-preserving pass (bilateral-lite).
 */
@UnstableApi
object UpscaleEffectChain {

    const val MAX_TARGET_HEIGHT = 2160

    fun build(context: Context, profile: UpscaleProfile, sourceHeight: Int): List<Effect> {
        // An explicit user-shader chain overrides the engine's own selection.
        UserShaderChain.load(context, profile)?.let { custom ->
            if (custom.passes.isNotEmpty()) return listOf(UserShaderGlEffect(custom))
        }
        val anime4kChain = Anime4KChain.load(context, profile)
        if (anime4kChain.passes.isNotEmpty()) return listOf(UserShaderGlEffect(anime4kChain))
        return buildLegacyChain(profile, sourceHeight)
    }

    private fun buildLegacyChain(profile: UpscaleProfile, sourceHeight: Int): List<Effect> = buildList {
        profile.passes.forEach { pass ->
            when (pass) {
                is UpscalePass.Denoise -> add(DenoiseGlEffect(pass.strength.coerceIn(0f, 1f)))
                is UpscalePass.Sharpen -> add(SharpenGlEffect(pass.strength.coerceIn(0f, 1f)))
                is UpscalePass.Upscale -> if (sourceHeight > 0) {
                    val target = (sourceHeight * pass.factor).roundToInt().coerceAtMost(MAX_TARGET_HEIGHT)
                    if (target > sourceHeight) {
                        add(Presentation.createForHeight(target))
                    }
                }
            }
        }
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

@UnstableApi
class SharpenGlEffect(private val strength: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SingleTexturePassShaderProgram(useHdr, FRAGMENT_SHARPEN, strength)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = strength <= 0f
}

@UnstableApi
class DenoiseGlEffect(private val strength: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SingleTexturePassShaderProgram(useHdr, FRAGMENT_DENOISE, strength)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = strength <= 0f
}

/**
 * Shared scaffolding for a single-texture GLSL ES 1.00 fragment pass
 * (the mpv user-shader format is not ported here; the passes' math
 * is rewritten for a plain fragment shader).
 */
@UnstableApi
private class SingleTexturePassShaderProgram(
    useHighPrecisionColorComponents: Boolean,
    fragmentShader: String,
    private val strength: Float,
) : BaseGlShaderProgram(useHighPrecisionColorComponents, 1) {

    private val glProgram = GlProgram(VERTEX_SHADER, fragmentShader)
    private var width = 0
    private var height = 0

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexelSize", floatArrayOf(1f / width, 1f / height))
            glProgram.setFloatUniform("uStrength", strength)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw androidx.media3.common.VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw androidx.media3.common.VideoFrameProcessingException(e)
        }
    }
}

private const val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

/**
 * Luma-guided unsharp mask with anti-ringing (clamped to the neighborhood min/max) —
 * a simplified single-pass analog of Anime4K Restore/Sharpen.
 */
private const val FRAGMENT_SHARPEN = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uStrength;
varying vec2 vTexSamplingCoord;

void main() {
  vec3 c = texture2D(uTexSampler, vTexSamplingCoord).rgb;
  vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb;
  vec3 s = texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexelSize.y)).rgb;
  vec3 e = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexelSize.x, 0.0)).rgb;

  vec3 blur = (n + s + e + w + c) * 0.2;
  vec3 sharpened = c + (c - blur) * (uStrength * 1.5);

  vec3 minC = min(c, min(min(n, s), min(e, w)));
  vec3 maxC = max(c, max(max(n, s), max(e, w)));
  gl_FragColor = vec4(clamp(sharpened, minC, maxC), 1.0);
}
"""

/**
 * Bilateral-lite: neighborhood averaging weighted by color proximity —
 * suppresses noise/blockiness while preserving line-art edges.
 */
private const val FRAGMENT_DENOISE = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uStrength;
varying vec2 vTexSamplingCoord;

void main() {
  vec3 c = texture2D(uTexSampler, vTexSamplingCoord).rgb;
  vec3 acc = c;
  float weightSum = 1.0;
  for (int x = -1; x <= 1; x++) {
    for (int y = -1; y <= 1; y++) {
      if (x == 0 && y == 0) continue;
      vec2 offset = vec2(float(x), float(y)) * uTexelSize;
      vec3 px = texture2D(uTexSampler, vTexSamplingCoord + offset).rgb;
      float diff = length(px - c);
      float w = exp(-diff * diff * 40.0);
      acc += px * w;
      weightSum += w;
    }
  }
  vec3 smoothed = acc / weightSum;
  gl_FragColor = vec4(mix(c, smoothed, uStrength), 1.0);
}
"""
