package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.util.Log
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.engine.media3.usershader.MpvUserShaderParser
import com.rinwave.sakuro.engine.media3.usershader.ShaderDocument
import java.io.File

/**
 * Loads an explicit [UpscaleProfile.shaderChain] from the imported user-shader
 * directory (shared with engine-mpv, see [UserShaderStore.RELATIVE_DIR]) into
 * one merged [ShaderDocument] for the generic runtime.
 */
internal object UserShaderChain {

    /** Null — no custom chain, or it failed to load (falls back to the standard path). */
    fun load(context: Context, profile: UpscaleProfile): ShaderDocument? {
        if (profile.shaderChain.isEmpty()) return null
        val dir = File(context.filesDir, UserShaderStore.RELATIVE_DIR)
        return runCatching {
            ShaderDocument.merge(
                profile.shaderChain.map { name ->
                    MpvUserShaderParser.parse(File(dir, name).readText())
                },
            )
        }.onSuccess {
            Log.i(TAG, "custom chain ${profile.shaderChain} → ${it.passes.size} passes")
        }.onFailure {
            Log.w(TAG, "custom shader chain '${profile.shaderChain}' failed to load: ${it.message}")
        }.getOrNull()
    }

    private const val TAG = "UserShaderChain"
}
