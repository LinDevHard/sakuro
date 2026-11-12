package com.rinwave.sakuro.core.player

import android.view.SurfaceHolder

/**
 * An Android engine that can render into a plain [SurfaceHolder]. The benchmark
 * and engine-specific renderers bind surfaces through this instead of checking
 * concrete engine classes.
 */
interface SurfaceVideoOutput {
    fun attachSurface(holder: SurfaceHolder)

    /** The surface buffer size; engines that size from the view itself ignore it. */
    fun resizeSurface(width: Int, height: Int)

    fun detachSurface()

    /** Called once before reference captures (e.g. exact seeking); no-op by default. */
    fun prepareForCapture() = Unit
}
