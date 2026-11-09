package com.rinwave.media3.usershader

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log

/**
 * What this device's GPU offers the user-shader runtime, probed once in a
 * throwaway offscreen context so the app can tell — before playback — which
 * shaders will run here.
 */
public data class GpuCapabilities(
    public val glesMajor: Int,
    public val glesMinor: Int,
    /** `GL_RENDERER`, e.g. "Adreno (TM) 740". */
    public val renderer: String,
    /** `GL_VENDOR`. */
    public val vendor: String,
    /** Full `GL_VERSION` string (driver build included). */
    public val glVersion: String,
    /**
     * An `RGBA16F` framebuffer is complete. Every pass renders into FP16
     * targets, so without it no shader graph can run at all.
     */
    public val fp16Renderable: Boolean,
) {
    /** ES 3.1+: compute passes, `//!BUFFER`, storage images and `textureGather`. */
    public val es31: Boolean get() = glesMajor > 3 || (glesMajor == 3 && glesMinor >= 1)

    /** One line for logs and the debug overlay. */
    public fun summary(): String =
        "$renderer · ES $glesMajor.$glesMinor · fp16 ${if (fp16Renderable) "yes" else "no"}"

    internal fun runtimeCapabilities(): RuntimeCapabilities =
        RuntimeCapabilities(gather = es31, compute = es31, ssbo = es31)

    public companion object {
        private const val TAG = "UserShaderGpu"
        private const val EGL_OPENGL_ES3_BIT = 0x40
        private const val CHANNEL_BITS = 8

        /**
         * Creates a 1×1 pbuffer ES 3 context, reads the version and strings and
         * test-renders an `RGBA16F` FBO the way the runtime does. Returns null
         * when no ES 3 context can be created (the runtime cannot run either).
         * Blocks for a few milliseconds — call it off the main thread.
         */
        // Vendor drivers fail in creative ways; a broken probe must never crash the app.
        @Suppress("TooGenericExceptionCaught")
        public fun probe(): GpuCapabilities? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
            var context: EGLContext = EGL14.EGL_NO_CONTEXT
            var surface: EGLSurface = EGL14.EGL_NO_SURFACE
            return try {
                val config = chooseConfig(display) ?: return null
                context = EGL14.eglCreateContext(
                    display, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
                )
                if (context == EGL14.EGL_NO_CONTEXT) return null
                surface = EGL14.eglCreatePbufferSurface(
                    display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
                )
                if (surface == EGL14.EGL_NO_SURFACE) return null
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                readCapabilities()
            } catch (e: RuntimeException) {
                Log.w(TAG, "GPU probe failed", e)
                null
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                // eglTerminate is skipped on purpose: the default display is
                // shared with Media3 and the UI in this process.
            }
        }

        private fun chooseConfig(display: EGLDisplay): EGLConfig? {
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, CHANNEL_BITS,
                EGL14.EGL_GREEN_SIZE, CHANNEL_BITS,
                EGL14.EGL_BLUE_SIZE, CHANNEL_BITS,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val found = EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
            return if (found && count[0] > 0) configs[0] else null
        }

        private fun readCapabilities(): GpuCapabilities {
            val major = IntArray(1)
            val minor = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, major, 0)
            GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, minor, 0)
            return GpuCapabilities(
                glesMajor = major[0],
                glesMinor = minor[0],
                renderer = GLES30.glGetString(GLES30.GL_RENDERER).orEmpty(),
                vendor = GLES30.glGetString(GLES30.GL_VENDOR).orEmpty(),
                glVersion = GLES30.glGetString(GLES30.GL_VERSION).orEmpty(),
                fp16Renderable = fp16FramebufferComplete(),
            )
        }

        /** Same allocation as the runtime's pass targets: immutable RGBA16F + FBO. */
        private fun fp16FramebufferComplete(): Boolean {
            val texture = IntArray(1)
            val fbo = IntArray(1)
            GLES30.glGenTextures(1, texture, 0)
            GLES30.glGenFramebuffers(1, fbo, 0)
            try {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA16F, 4, 4)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
                GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture[0], 0,
                )
                val complete = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
                    GLES30.GL_FRAMEBUFFER_COMPLETE
                return complete && GLES30.glGetError() == GLES30.GL_NO_ERROR
            } finally {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glDeleteFramebuffers(1, fbo, 0)
                GLES30.glDeleteTextures(1, texture, 0)
            }
        }
    }
}
