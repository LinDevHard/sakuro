package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES30

/**
 * What the current GL context can execute beyond the ES 3.0 baseline.
 * Probed once per [UserShaderProgram] configuration on the GL thread; the
 * planner rejects passes that need missing capabilities so the chain degrades
 * to passthrough instead of failing to compile.
 */
internal data class RuntimeCapabilities(
    /** `textureGather` and the `NAME_gather` symbols (ES 3.1). */
    val gather: Boolean,
    /** `//!COMPUTE` passes via compute shaders and image load/store (ES 3.1). */
    val compute: Boolean,
) {
    companion object {
        /** ES 3.0: fragment-only, no gather. */
        val BASELINE = RuntimeCapabilities(gather = false, compute = false)

        /** Everything the runtime currently knows how to use. */
        val ES31 = RuntimeCapabilities(gather = true, compute = true)

        /** Reads the context version; call on the GL thread. */
        fun probe(): RuntimeCapabilities {
            val major = IntArray(1)
            val minor = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, major, 0)
            GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, minor, 0)
            val es31 = major[0] > 3 || (major[0] == 3 && minor[0] >= 1)
            return RuntimeCapabilities(gather = es31, compute = es31)
        }
    }
}
