package com.rinwave.sakuro.engine.media3

import android.content.Context
import com.rinwave.media3.usershader.ShaderCheck
import com.rinwave.media3.usershader.ShaderParam
import com.rinwave.media3.usershader.UserShaderValidator
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.ShaderSupport
import com.rinwave.sakuro.core.upscale.ShaderTunable
import com.rinwave.sakuro.core.upscale.UserShaderStore
import java.io.File

/**
 * Reads the `//!PARAM` blocks of imported and bundled shaders so the UI can
 * offer a control per tunable, and tells whether this device's GPU can run
 * them. Backed by the same parser and planner the Media3 runtime uses, so what
 * the UI shows is exactly what the engine will do.
 *
 * Results are cached per file version: re-importing a file under the same
 * name changes its timestamp and size, bundled assets never change.
 */
class Media3ShaderInspector(context: Context) : ShaderInspector {

    private val appContext = context.applicationContext
    private val importedDir = File(appContext.filesDir, UserShaderStore.RELATIVE_DIR)
    private val tunablesCache = HashMap<String, List<ShaderTunable>>()
    private val supportCache = HashMap<String, ShaderSupport>()

    override fun tunables(fileName: String): List<ShaderTunable> = tunablesCache.getOrPut(cacheKey(fileName)) {
        val document = UserShaderChain.loadByName(appContext, fileName) ?: return@getOrPut emptyList()
        document.params.filterNot { it.constant }.map { it.toTunable() }
    }

    override fun support(fileName: String): ShaderSupport {
        // Without an ES 3 context nothing can be decided; the runtime will say why at playback.
        val gpu = DeviceGpu.capabilities ?: return ShaderSupport.Unknown
        return supportCache.getOrPut(cacheKey(fileName)) {
            val document = UserShaderChain.loadByName(appContext, fileName) ?: return@getOrPut ShaderSupport.Unknown
            when (val check = UserShaderValidator.check(document, gpu)) {
                ShaderCheck.Ok -> ShaderSupport.Supported
                is ShaderCheck.Unsupported -> ShaderSupport.Unsupported(check.reason)
                is ShaderCheck.Invalid -> ShaderSupport.Unsupported(check.reason)
            }
        }
    }

    private fun cacheKey(fileName: String): String {
        val imported = File(importedDir, fileName)
        return if (imported.isFile) "$fileName@${imported.lastModified()}:${imported.length()}" else fileName
    }

    /**
     * `CONSTANT` params are compile-time constants the shader author does not
     * intend to be tuned, so they are not offered; everything else is.
     */
    private fun ShaderParam.toTunable(): ShaderTunable {
        val integral = enum || type == "int" || type == "uint"
        return ShaderTunable(
            name = name,
            description = desc,
            default = defaultValue,
            minimum = minimum,
            maximum = maximum,
            integral = integral,
        )
    }
}
