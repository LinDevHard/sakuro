package com.rinwave.sakuro.engine.media3.anime4k

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram

/**
 * A mini render-graph runtime for mpv user-shaders on top of a single [BaseGlShaderProgram]
 * (docs/anime4k-media3-port-plan.md §2, §4). Media3 sees one effect; the whole
 * multi-pass nature of Anime4K (conv chains, depth-to-space, residual) runs
 * inside `drawFrame` over its own FP16 FBOs.
 *
 * Passes arrive already concatenated from several `.glsl` files of the preset
 * (Clamp→Denoise→Restore→Upscale). The resolution of each intermediate texture is
 * computed by [Anime4KGraphPlanner]; the result of the `MAIN` stage is presented into the
 * Media3 output texture.
 *
 * FP16 is a hard requirement (CNN feature maps go beyond [0,1] and go negative).
 * If color-renderable FP16 is unavailable / the FBO is incomplete, the runtime degrades to
 * a simple passthrough (the frame without upscaling) instead of breaking the pipeline.
 */
@UnstableApi
internal class Anime4KShaderProgram(
    private val passes: List<UserShaderPass>,
    /** Target-size multiplier for gating `//!WHEN` (upscale larger than the input). */
    private val outputGateScale: Int = OUTPUT_GATE_SCALE,
) : BaseGlShaderProgram(HIGH_PRECISION, TEXTURE_POOL_CAPACITY) {

    private data class TexRef(val texId: Int, val width: Int, val height: Int)
    private data class Target(val texId: Int, val fbo: Int, val width: Int, val height: Int)

    private var plan: GraphPlan = GraphPlan(emptyList(), 0, 0)
    private var passPrograms: List<GlProgram> = emptyList()
    private var targets: List<Target> = emptyList()
    private lateinit var presentProgram: GlProgram

    private var inputWidth = 0
    private var inputHeight = 0
    private var degraded = false

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        releaseGraph()

        plan = Anime4KGraphPlanner.plan(
            passes,
            inputWidth,
            inputHeight,
            inputWidth * outputGateScale,
            inputHeight * outputGateScale,
        )

        try {
            presentProgram = GlProgram(ShaderPreamble.VERTEX_SHADER, PRESENT_FRAGMENT).apply {
                setBufferAttribute(POSITION_ATTR, GlUtil.getNormalizedCoordinateBounds(), COORD_SIZE)
            }
            passPrograms = plan.passes.map { planned ->
                val fragment = ShaderPreamble.fragmentShader(planned.pass, isFinal = false)
                GlProgram(ShaderPreamble.VERTEX_SHADER, fragment).apply {
                    setBufferAttribute(POSITION_ATTR, GlUtil.getNormalizedCoordinateBounds(), COORD_SIZE)
                }
            }
            targets = plan.passes.map { createFp16Target(it.outWidth, it.outHeight) }
            degraded = false
        } catch (e: GlUtil.GlException) {
            // No FP16 targets / the shader did not build — degrade to passthrough.
            Log.w(TAG, "Anime4K graph was not built, passthrough: ${e.message}")
            degraded = true
            releaseGraph()
        }

        // On degradation we return the source size (no upscale is applied).
        return if (degraded) Size(inputWidth, inputHeight) else Size(plan.outputWidth, plan.outputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (degraded) {
                blitInput(inputTexId, inputWidth, inputHeight)
                return
            }
            runGraph(inputTexId)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun runGraph(inputTexId: Int) {
        // The Media3 output-texture FBO, bound by the base class before drawFrame.
        val outputFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, outputFbo, 0)

        // Frame stages PREKERNEL/NATIVE are canonicalized to MAIN (see the planner),
        // so the map holds a single frame slot.
        val current = HashMap<String, TexRef>()
        current[MAIN] = TexRef(inputTexId, inputWidth, inputHeight)

        plan.passes.forEachIndexed { i, planned ->
            val program = passPrograms[i]
            val target = targets[i]
            GlUtil.focusFramebufferUsingCurrentContext(target.fbo, target.width, target.height)
            program.use()
            // The current program id — so we set only the ACTIVE uniforms:
            // the GLSL compiler drops unused ones (e.g. _size is only needed by
            // depth-to-space), GlProgram does not register them and fails on set;
            // yet bindAttributesAndUniforms requires a value for every one it knows,
            // so we set them through GlProgram (not around it) but gated by activity.
            val programId = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, programId, 0)
            planned.pass.binds.distinct().forEachIndexed { unit, bind ->
                val ref = current[Anime4KGraphPlanner.canonicalStage(bind, planned.pass.hook)]
                    ?: error("Anime4K: '${planned.pass.desc}' binds a missing texture '$bind'")
                val sampler = ShaderPreamble.samplerUniform(bind)
                if (isActive(programId[0], sampler)) program.setSamplerTexIdUniform(sampler, ref.texId, unit)
                val sizeName = ShaderPreamble.sizeUniform(bind)
                if (isActive(programId[0], sizeName)) {
                    program.setFloatsUniform(sizeName, floatArrayOf(ref.width.toFloat(), ref.height.toFloat()))
                }
                val ptName = ShaderPreamble.pointUniform(bind)
                if (isActive(programId[0], ptName)) {
                    program.setFloatsUniform(ptName, floatArrayOf(1f / ref.width, 1f / ref.height))
                }
            }
            program.bindAttributesAndUniforms()
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            val saved = Anime4KGraphPlanner.canonicalStage(planned.pass.save, planned.pass.hook)
            current[saved] = TexRef(target.texId, target.width, target.height)
        }

        // Presentation: MAIN → the Media3 output texture, alpha is forced to 1.
        val main = current.getValue(MAIN)
        GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], plan.outputWidth, plan.outputHeight)
        presentProgram.use()
        presentProgram.setSamplerTexIdUniform(PRESENT_SAMPLER, main.texId, 0)
        presentProgram.bindAttributesAndUniforms()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Passthrough on degradation: copy the input into the Media3 output texture. */
    private fun blitInput(inputTexId: Int, width: Int, height: Int) {
        val outputFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, outputFbo, 0)
        GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], width, height)
        presentProgram.use()
        presentProgram.setSamplerTexIdUniform(PRESENT_SAMPLER, inputTexId, 0)
        presentProgram.bindAttributesAndUniforms()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        releaseGraph()
        if (::presentProgram.isInitialized) {
            try {
                presentProgram.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    private fun releaseGraph() {
        passPrograms.forEach { runCatching { it.delete() } }
        passPrograms = emptyList()
        targets.forEach { target ->
            GLES30.glDeleteFramebuffers(1, intArrayOf(target.fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(target.texId), 0)
        }
        targets = emptyList()
    }

    /** Whether a uniform is active in the program (otherwise the GLSL compiler dropped it). */
    private fun isActive(programId: Int, name: String): Boolean =
        GLES20.glGetUniformLocation(programId, name) >= 0

    private fun createFp16Target(width: Int, height: Int): Target {
        val texture = IntArray(1)
        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture[0], 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(1, texture, 0)
            throw GlUtil.GlException("Anime4K: FP16 FBO is incomplete (status=$status) — color-renderable RGBA16F is unavailable")
        }
        return Target(texture[0], fbo[0], width, height)
    }

    private companion object {
        const val TAG = "Anime4K"
        const val HIGH_PRECISION = true
        const val TEXTURE_POOL_CAPACITY = 1
        const val MAIN = "MAIN"
        const val POSITION_ATTR = "a_position"
        const val PRESENT_SAMPLER = "uTex"
        const val COORD_SIZE = 4

        /** We compute OUTPUT as N× the input so the `//!WHEN` upscale gates trigger. */
        const val OUTPUT_GATE_SCALE = 4

        const val PRESENT_FRAGMENT = """#version 300 es
precision highp float;
uniform sampler2D uTex;
in vec2 v_texcoord;
out vec4 frag_out;
void main() {
  frag_out = vec4(texture(uTex, v_texcoord).rgb, 1.0);
}
"""
    }
}
