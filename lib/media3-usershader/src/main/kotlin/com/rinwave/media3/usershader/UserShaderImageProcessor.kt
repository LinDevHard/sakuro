package com.rinwave.media3.usershader

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A processed image plus what the shader graph actually did to it. */
public data class UserShaderImageResult(
    public val bitmap: Bitmap,
    /** [ShaderGraphRuntimeStats.degradedReason] is set when the graph fell back to a plain copy. */
    public val stats: ShaderGraphRuntimeStats,
)

/**
 * Runs an mpv user-shader chain on still images — the same graph
 * [UserShaderGlEffect] runs on video frames, without a Media3 pipeline.
 *
 * Owns a private offscreen GL context. Calls are serialized; any thread may
 * call [process], but each call blocks while the GPU works, so keep it off the
 * main thread. [close] releases the GL resources; the processor cannot be used
 * afterwards.
 *
 * Images are treated as opaque sRGB: the output alpha is 1, so transparency
 * is lost (premultiplied edges darken). Input and output must fit the GPU's
 * maximum texture size.
 */
@UnstableApi
public class UserShaderImageProcessor(
    document: ShaderDocument,
    paramValues: Map<String, Float> = emptyMap(),
    /** The `OUTPUT` size for `//!WHEN` gates; null keeps every upscale gate open (4× fallback). */
    targetSize: Size? = null,
) : Closeable {

    private val egl = OffscreenEgl.create()
        ?: throw UserShaderException("no OpenGL ES 3 context is available on this device")
    private var stats: ShaderGraphRuntimeStats? = null
    private val program: UserShaderProgram
    private var configuredSize: Size? = null
    private var outputSize = Size(0, 0)
    private var closed = false

    init {
        require(targetSize == null || (targetSize.width > 0 && targetSize.height > 0)) {
            "targetSize must be positive, was $targetSize"
        }
        program = UserShaderProgram(document, targetSize, paramValues) { stats = it }
    }

    /**
     * Runs the chain on [input] (any config; non-ARGB_8888 bitmaps are
     * converted first) and returns a new ARGB_8888 bitmap at the graph's output
     * size. Throws [UserShaderException] when the GL work fails.
     */
    @Synchronized
    public fun process(input: Bitmap): UserShaderImageResult {
        check(!closed) { "processor is closed" }
        bindContext()
        try {
            val source = if (input.config == Bitmap.Config.ARGB_8888) {
                input
            } else {
                input.copy(Bitmap.Config.ARGB_8888, false)
            }
            checkTextureSize(source.width, source.height)
            if (configuredSize != Size(source.width, source.height)) {
                outputSize = program.configure(source.width, source.height)
                configuredSize = Size(source.width, source.height)
            }
            checkTextureSize(outputSize.width, outputSize.height)
            val output = render(source)
            if (source !== input) source.recycle()
            return UserShaderImageResult(output, checkNotNull(stats))
        } catch (e: VideoFrameProcessingException) {
            throw UserShaderException("shader graph failed: ${e.message}", e)
        } catch (e: GlUtil.GlException) {
            throw UserShaderException("GL setup failed: ${e.message}", e)
        } finally {
            egl.releaseCurrent()
        }
    }

    private fun bindContext() {
        if (!egl.makeCurrent()) throw UserShaderException("could not make the offscreen GL context current")
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            if (egl.makeCurrent()) program.release()
        } finally {
            egl.destroy()
        }
    }

    private fun render(source: Bitmap): Bitmap {
        val textures = IntArray(2)
        val fbo = IntArray(1)
        GLES30.glGenTextures(2, textures, 0)
        GLES30.glGenFramebuffers(1, fbo, 0)
        try {
            val (inputTex, outputTex) = textures.toList()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTex)
            setSampling()
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, source, 0)

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, outputTex)
            setSampling()
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, outputSize.width, outputSize.height)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, outputTex, 0,
            )
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                throw UserShaderException("output framebuffer is incomplete")
            }

            // The program presents into whatever framebuffer is bound — ours.
            program.drawFrame(inputTex, 0)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            val pixels = ByteBuffer.allocateDirect(outputSize.width * outputSize.height * BYTES_PER_PIXEL)
                .order(ByteOrder.nativeOrder())
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            GLES30.glReadPixels(
                0, 0, outputSize.width, outputSize.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels,
            )
            val error = GLES30.glGetError()
            if (error != GLES30.GL_NO_ERROR) throw UserShaderException("reading the result failed: GL error $error")
            // Texture row 0 is the bitmap's top row and stays row 0 through the
            // graph, so the read-back rows are already in bitmap order.
            return Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(pixels.rewind())
            }
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(2, textures, 0)
        }
    }

    private fun setSampling() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun checkTextureSize(width: Int, height: Int) {
        val max = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
        if (width > max[0] || height > max[0]) {
            throw UserShaderException("${width}x$height exceeds this GPU's texture limit of ${max[0]} px")
        }
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
