package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * A mini render-graph runtime for mpv user-shaders on top of a single
 * [BaseGlShaderProgram]. Media3 sees one effect; the whole multi-pass graph
 * (conv chains, depth-to-space, residual) runs inside `drawFrame` over its own
 * FP16 FBOs.
 *
 * Passes arrive already concatenated from the `.glsl` files of the chain. The
 * resolution of each intermediate texture is computed by [ShaderGraphPlanner];
 * the result of the `MAIN` stage is presented into the Media3 output texture.
 *
 * FP16 is a hard requirement (CNN feature maps go beyond [0,1] and go negative).
 * If color-renderable FP16 is unavailable, the plan fails ([UserShaderException])
 * or the shader does not build ([GlUtil.GlException]) — the runtime degrades to
 * a simple passthrough (the frame without upscaling) instead of breaking the pipeline.
 */
@UnstableApi
internal class UserShaderProgram(
    private val document: ShaderDocument,
    /** Target-size multiplier for gating `//!WHEN` (upscale larger than the input). */
    private val outputGateScale: Int = OUTPUT_GATE_SCALE,
) : BaseGlShaderProgram(HIGH_PRECISION, TEXTURE_POOL_CAPACITY) {

    private data class TexRef(val texId: Int, val width: Int, val height: Int)
    private data class Target(val texId: Int, val fbo: Int, val width: Int, val height: Int)

    private var plan: GraphPlan = GraphPlan(emptyList(), 0, 0, emptyList())
    private var passPrograms: List<GlProgram> = emptyList()
    private var targets: List<Target> = emptyList()
    private var lutTextures: Map<String, TexRef> = emptyMap()
    private lateinit var presentProgram: GlProgram

    private var inputWidth = 0
    private var inputHeight = 0
    private var degraded = false
    private var frameIndex = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        releaseGraph()

        try {
            presentProgram = GlProgram(ShaderPreamble.VERTEX_SHADER, PRESENT_FRAGMENT).apply {
                setBufferAttribute(POSITION_ATTR, GlUtil.getNormalizedCoordinateBounds(), COORD_SIZE)
            }
            plan = ShaderGraphPlanner.plan(
                document,
                inputWidth,
                inputHeight,
                inputWidth * outputGateScale,
                inputHeight * outputGateScale,
            )
            plan.skipped.forEach { Log.i(TAG, "pass '$it' skipped: its hooks never fire in this pipeline") }
            passPrograms = plan.passes.map { planned ->
                val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
                GlProgram(ShaderPreamble.VERTEX_SHADER, fragment).apply {
                    setBufferAttribute(POSITION_ATTR, GlUtil.getNormalizedCoordinateBounds(), COORD_SIZE)
                }
            }
            targets = plan.passes.map { createFp16Target(it.outWidth, it.outHeight) }
            lutTextures = uploadLutTextures()
            degraded = false
        } catch (e: GlUtil.GlException) {
            degradeToPassthrough(e)
        } catch (e: UserShaderException) {
            degradeToPassthrough(e)
        }

        // On degradation we return the source size (no upscale is applied).
        return if (degraded) Size(inputWidth, inputHeight) else Size(plan.outputWidth, plan.outputHeight)
    }

    /** No FP16 targets / an unsupported feature / a broken shader — play the frame as-is. */
    private fun degradeToPassthrough(cause: Exception) {
        Log.w(TAG, "shader graph was not built, passthrough: ${cause.message}")
        degraded = true
        releaseGraph()
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (degraded) {
                blitInput(inputTexId, inputWidth, inputHeight)
                return
            }
            runGraph(inputTexId)
            frameIndex++
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        } catch (e: UserShaderException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun runGraph(inputTexId: Int) {
        // The Media3 output-texture FBO, bound by the base class before drawFrame.
        val outputFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, outputFbo, 0)

        // Frame stages PREKERNEL/NATIVE are canonicalized to MAIN (see the planner),
        // so the map holds a single frame slot. Custom //!TEXTURE LUTs are static inputs.
        val current = HashMap<String, TexRef>()
        current.putAll(lutTextures)
        current[MAIN] = TexRef(inputTexId, inputWidth, inputHeight)

        plan.passes.forEachIndexed { i, planned ->
            // The post-scale slot starts as the final MAIN (our kernel is the identity).
            if (planned.stage == ShaderStages.POST && ShaderStages.POST !in current) {
                current[ShaderStages.POST] = current.getValue(MAIN)
            }
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
                val ref = current[ShaderGraphPlanner.canonicalStage(bind, planned.stage)]
                    ?: throw UserShaderException("'${planned.pass.desc}' binds a missing texture '$bind'")
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
            setGlobalUniforms(program, programId[0])
            program.bindAttributesAndUniforms()
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            val saved = ShaderGraphPlanner.canonicalStage(planned.pass.save, planned.stage)
            current[saved] = TexRef(target.texId, target.width, target.height)
        }

        // Presentation: the final frame slot → the Media3 output texture, alpha is
        // forced to 1 and the accumulated //!OFFSET is compensated by shifting sampling.
        val frame = current[plan.presentSlot] ?: current.getValue(MAIN)
        GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], plan.outputWidth, plan.outputHeight)
        presentProgram.use()
        presentProgram.setSamplerTexIdUniform(PRESENT_SAMPLER, frame.texId, 0)
        presentProgram.setFloatsUniform(
            PRESENT_OFFSET,
            floatArrayOf(plan.offsetX / frame.width, plan.offsetY / frame.height),
        )
        presentProgram.bindAttributesAndUniforms()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** The spec globals (`frame`, `random`, sizes) — set only when the pass uses them. */
    private fun setGlobalUniforms(program: GlProgram, programId: Int) {
        if (isActive(programId, ShaderPreamble.UNIFORM_FRAME)) {
            program.setIntUniform(ShaderPreamble.UNIFORM_FRAME, frameIndex)
        }
        if (isActive(programId, ShaderPreamble.UNIFORM_RANDOM)) {
            program.setFloatUniform(ShaderPreamble.UNIFORM_RANDOM, Random.nextFloat())
        }
        if (isActive(programId, ShaderPreamble.UNIFORM_INPUT_SIZE)) {
            program.setFloatsUniform(
                ShaderPreamble.UNIFORM_INPUT_SIZE,
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
            )
        }
        if (isActive(programId, ShaderPreamble.UNIFORM_TARGET_SIZE)) {
            program.setFloatsUniform(
                ShaderPreamble.UNIFORM_TARGET_SIZE,
                floatArrayOf(plan.outputWidth.toFloat(), plan.outputHeight.toFloat()),
            )
        }
        if (isActive(programId, ShaderPreamble.UNIFORM_TEX_OFFSET)) {
            program.setFloatsUniform(ShaderPreamble.UNIFORM_TEX_OFFSET, floatArrayOf(0f, 0f))
        }
    }

    /** Passthrough on degradation: copy the input into the Media3 output texture. */
    private fun blitInput(inputTexId: Int, width: Int, height: Int) {
        val outputFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, outputFbo, 0)
        GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], width, height)
        presentProgram.use()
        presentProgram.setSamplerTexIdUniform(PRESENT_SAMPLER, inputTexId, 0)
        presentProgram.setFloatsUniform(PRESENT_OFFSET, floatArrayOf(0f, 0f))
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
        lutTextures.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it.texId), 0) }
        lutTextures = emptyMap()
    }

    /** Uploads the `//!TEXTURE` blocks referenced by the planned passes as static LUTs. */
    private fun uploadLutTextures(): Map<String, TexRef> {
        val bound = plan.passes
            .flatMap { planned -> planned.pass.binds.map { ShaderGraphPlanner.canonicalStage(it, planned.stage) } }
            .toSet()
        return document.textures.filter { it.name in bound }.associate { it.name to uploadLut(it) }
    }

    private fun uploadLut(texture: ShaderTexture): TexRef {
        val format = ShaderTextureFormats.validate(texture)
        val data = checkNotNull(texture.data)
        val height = texture.height ?: 1
        val buffer = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
        buffer.put(data).position(0)

        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        // Texel rows are tightly packed regardless of format width.
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, format.internalFormat, texture.width, height, 0,
            format.format, format.type, buffer,
        )
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, DEFAULT_UNPACK_ALIGNMENT)
        val filter = if (texture.filterLinear) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        val wrap = ShaderTextureFormats.wrapMode(texture.border)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GlUtil.checkGlError()
        return TexRef(tex[0], texture.width, height)
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
            throw GlUtil.GlException(
                "FP16 FBO is incomplete (status=$status) — color-renderable RGBA16F is unavailable",
            )
        }
        return Target(texture[0], fbo[0], width, height)
    }

    private companion object {
        /** Grep tag for on-device diagnostics: `adb logcat | grep UserShader`. */
        const val TAG = "UserShader"
        const val HIGH_PRECISION = true
        const val TEXTURE_POOL_CAPACITY = 1
        const val MAIN = "MAIN"
        const val POSITION_ATTR = "a_position"
        const val PRESENT_SAMPLER = "uTex"
        const val PRESENT_OFFSET = "uOffset"
        const val COORD_SIZE = 4

        /** We compute OUTPUT as N× the input so the `//!WHEN` upscale gates trigger. */
        const val OUTPUT_GATE_SCALE = 4

        const val DEFAULT_UNPACK_ALIGNMENT = 4

        const val PRESENT_FRAGMENT = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform vec2 uOffset;
in vec2 v_texcoord;
out vec4 frag_out;
void main() {
  frag_out = vec4(texture(uTex, v_texcoord + uOffset).rgb, 1.0);
}
"""
    }
}
