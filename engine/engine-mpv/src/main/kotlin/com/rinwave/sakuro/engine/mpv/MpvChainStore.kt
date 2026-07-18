package com.rinwave.sakuro.engine.mpv

import android.content.Context
import android.util.Log
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.UserShaderStore
import java.io.File

/**
 * Resolves a preset's shader-chain names into absolute paths for `glsl-shaders`.
 * A name is an imported file first ([UserShaderStore.RELATIVE_DIR], shared with
 * Media3), then a vendored [BundledShaders] asset — so an import can shadow a
 * bundled shader of the same name.
 *
 * Files carrying `//!PARAM` blocks (and any bundled asset — libmpv reads only
 * the file system) are deployed into the store directory; params are folded
 * into defines by [MpvShaderMaterializer] since classic `vo=gpu` does not
 * know the directive.
 */
internal class MpvChainStore(private val context: Context) {

    /**
     * Paths in chain order; null — a name resolved nowhere or deployment failed.
     * [params] — per-file `//!PARAM` overrides baked in during materialization.
     */
    fun resolve(
        names: List<String>,
        params: Map<String, Map<String, Float>> = emptyMap(),
    ): List<String>? = runCatching {
        names.map { resolveOne(it, params[it].orEmpty()) }
    }.onFailure { Log.w(TAG, "shader chain $names failed to resolve: ${it.message}") }.getOrNull()

    private fun resolveOne(name: String, overrides: Map<String, Float>): String {
        val imported = File(context.filesDir, UserShaderStore.RELATIVE_DIR).resolve(name)
        val source = if (imported.isFile) {
            imported.readText()
        } else {
            val bundled = requireNotNull(BundledShaders.byFileName(name)) {
                "shader '$name' is neither imported nor bundled"
            }
            context.assets.open(bundled.assetPath).bufferedReader().use { it.readText() }
        }
        val materialized = MpvShaderMaterializer.materialize(source, overrides)
        return if (materialized == null && imported.isFile) {
            imported.absolutePath
        } else {
            deploy(name, materialized ?: source)
        }
    }

    /** Writes the content under the store dir, skipping the write when unchanged. */
    private fun deploy(name: String, content: String): String {
        val dir = File(context.filesDir, STORE_DIR).apply { mkdirs() }
        val file = File(dir, name)
        if (!file.isFile || file.readText() != content) file.writeText(content)
        return file.absolutePath
    }

    private companion object {
        const val TAG = "MpvChainStore"

        /** Deployed bundled/materialized chain files (distinct from the Anime4K store). */
        const val STORE_DIR = "shaders/chain"
    }
}
