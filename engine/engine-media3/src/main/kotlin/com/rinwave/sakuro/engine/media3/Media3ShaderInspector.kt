package com.rinwave.sakuro.engine.media3

import android.content.Context
import com.rinwave.sakuro.core.upscale.ShaderInspector
import com.rinwave.sakuro.core.upscale.ShaderTunable
import com.rinwave.sakuro.engine.media3.usershader.ShaderParam

/**
 * Reads the `//!PARAM` blocks of imported and bundled shaders so the UI can
 * offer a control per tunable. Backed by the same parser the Media3 runtime
 * uses, so what the UI shows is exactly what the engines will bake in.
 *
 * Results are cached by file name: shader files do not change in place (an
 * import writes a new file), and the editor asks repeatedly while composing.
 */
class Media3ShaderInspector(context: Context) : ShaderInspector {

    private val appContext = context.applicationContext
    private val cache = HashMap<String, List<ShaderTunable>>()

    override fun tunables(fileName: String): List<ShaderTunable> = cache.getOrPut(fileName) {
        val document = UserShaderChain.loadByName(appContext, fileName) ?: return@getOrPut emptyList()
        document.params.filterNot { it.constant }.map { it.toTunable() }
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
