package com.rinwave.sakuro.engine.mpv

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Gives mpv the absolute paths to the vendored Anime4K shaders
 * (assets/anime4k, MIT, licenses/anime4k). libmpv reads `glsl-shaders`
 * only from the file system, so the assets are copied into filesDir on
 * first use; the directory is versioned — changing the shader version
 * in the assets produces a new copy, and old versions are removed.
 */
internal class MpvShaderStore(private val context: Context) {

    private val dir: File? by lazy { provision() }

    /** Paths in chain order; null — the copy failed and shaders are unavailable. */
    fun resolve(names: List<String>): List<String>? {
        val root = dir ?: return null
        return names.map { File(root, it).absolutePath }
    }

    private fun provision(): File? = runCatching {
        val root = File(context.filesDir, STORE_DIR)
        val target = File(root, VERSION)
        if (!target.isDirectory) {
            root.listFiles()?.forEach { it.deleteRecursively() }
            val staging = File(root, "$VERSION.tmp")
            staging.mkdirs()
            val assets = context.assets
            assets.list(ASSET_DIR).orEmpty().forEach { name ->
                assets.open("$ASSET_DIR/$name").use { input ->
                    File(staging, name).outputStream().use { input.copyTo(it) }
                }
            }
            check(staging.renameTo(target)) { "rename $staging -> $target" }
        }
        target
    }.onFailure { Log.w(TAG, "Failed to deploy shaders", it) }.getOrNull()

    private companion object {
        const val TAG = "MpvShaderStore"
        const val ASSET_DIR = "anime4k"
        const val STORE_DIR = "shaders/anime4k"

        /** Version of the vendored set; bump it when the assets are updated. */
        const val VERSION = "v4.0.1"
    }
}
