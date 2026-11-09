package com.rinwave.media3.usershader

import java.io.File
import kotlin.test.fail

/**
 * Real shaders shipped by Sakuro's engine-media3 module, used here as a
 * regression corpus. Unit tests run with this module as the working directory,
 * so walk up to the repository root and resolve the engine's assets from there.
 */
internal fun engineAsset(path: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "engine/engine-media3/src/main/assets/$path")
        if (candidate.exists()) return candidate
        dir = dir.parentFile
    }
    fail("engine asset $path not found above ${File("").absolutePath}")
}
