package com.rinwave.media3.usershader

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface

/**
 * A private ES 3 context on a 1×1 pbuffer, for work outside Media3's pipeline
 * (the GPU probe, still-image processing). All rendering goes to FBOs, so the
 * pbuffer only has to make the context current.
 *
 * The default display is shared with Media3 and the UI in this process, so it
 * is never terminated here.
 */
internal class OffscreenEgl private constructor(
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val surface: EGLSurface,
) {

    /** Makes the context current on the calling thread; false if the driver refuses. */
    fun makeCurrent(): Boolean = EGL14.eglMakeCurrent(display, surface, surface, context)

    /** Detaches the context from the calling thread so another thread may use it. */
    fun releaseCurrent() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun destroy() {
        releaseCurrent()
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT = 0x40
        private const val CHANNEL_BITS = 8

        /** Null when the device has no ES 3 context to offer. */
        fun create(): OffscreenEgl? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
            val config = chooseConfig(display) ?: return null
            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null
            val surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, context)
                return null
            }
            return OffscreenEgl(display, context, surface)
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
    }
}
