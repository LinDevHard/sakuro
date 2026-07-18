package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.util.Log
import com.rinwave.sakuro.core.upscale.BundledShaders
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.engine.media3.usershader.MpvUserShaderParser
import com.rinwave.sakuro.engine.media3.usershader.ShaderDocument
import java.io.File

/**
 * Loads an explicit [UpscaleProfile.shaderChain] into one merged [ShaderDocument]
 * for the generic runtime. A chain name resolves to an imported file first
 * (the directory shared with engine-mpv, [UserShaderStore.RELATIVE_DIR]), then
 * to a vendored asset from the [BundledShaders] registry — so an import can
 * shadow a bundled shader of the same name.
 */
internal object UserShaderChain {

    /** Null — no custom chain, or it failed to load (falls back to the standard path). */
    fun load(context: Context, profile: UpscaleProfile): ShaderDocument? {
        if (profile.shaderChain.isEmpty()) return null
        val dir = File(context.filesDir, UserShaderStore.RELATIVE_DIR)
        return runCatching {
            ShaderDocument.merge(
                profile.shaderChain.map { name ->
                    MpvUserShaderParser.parse(readShader(context, dir, name))
                },
            )
        }.onSuccess {
            Log.i(TAG, "custom chain ${profile.shaderChain} → ${it.passes.size} passes")
        }.onFailure {
            Log.w(TAG, "custom shader chain '${profile.shaderChain}' failed to load: ${it.message}")
        }.getOrNull()
    }

    /**
     * Loads one shader by chain name (imported first, bundled second) for the
     * parametric chain; null — unavailable or broken (the pass is skipped).
     */
    fun loadByName(context: Context, name: String): ShaderDocument? = runCatching {
        val dir = File(context.filesDir, UserShaderStore.RELATIVE_DIR)
        MpvUserShaderParser.parse(readShader(context, dir, name))
    }.onFailure {
        Log.w(TAG, "shader '$name' failed to load: ${it.message}")
    }.getOrNull()

    private fun readShader(context: Context, importedDir: File, name: String): String {
        val imported = File(importedDir, name)
        if (imported.isFile) return imported.readText()
        val bundled = BundledShaders.byFileName(name)
            ?: throw IllegalArgumentException("shader '$name' is neither imported nor bundled")
        return context.assets.open(bundled.assetPath).bufferedReader().use { it.readText() }
    }

    private const val TAG = "UserShaderChain"
}
