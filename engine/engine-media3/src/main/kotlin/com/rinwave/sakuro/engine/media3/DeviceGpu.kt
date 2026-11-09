package com.rinwave.sakuro.engine.media3

import android.util.Log
import com.rinwave.media3.usershader.GpuCapabilities

/**
 * The device GPU as the user-shader runtime sees it, probed once per process
 * in an offscreen context. Shader pickers, import, the debug overlay and the
 * benchmark all read the same result.
 */
object DeviceGpu {

    private const val TAG = "DeviceGpu"

    /** Null when no ES 3 context could be created; blocks on first access. */
    val capabilities: GpuCapabilities? by lazy {
        GpuCapabilities.probe().also { Log.i(TAG, it?.summary() ?: "GPU probe failed") }
    }

    /** Runs the probe on a background thread so the UI never waits for it. */
    fun warmUp() {
        Thread({ capabilities }, "gpu-probe").start()
    }
}
