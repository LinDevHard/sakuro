package com.rinwave.sakuro.core.upscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Imported mpv user-shader files (`.glsl`/`.hook`), referenced by presets via
 * [UpscaleProfile.shaderChain]. Both engines read the same directory
 * ([RELATIVE_DIR] under the app files dir): Media3 parses the files with its
 * own runtime, mpv gets them as `glsl-shaders` paths.
 */
interface UserShaderStore {

    /** Imported file names, sorted; updates after [import]/[delete]. */
    val shaders: StateFlow<List<String>>

    /**
     * Validates and saves shader [content] under a sanitized [fileName].
     * Returns the stored name, or a failure with a human-readable reason.
     */
    fun import(fileName: String, content: String): Result<String>

    fun delete(name: String)

    companion object {
        const val RELATIVE_DIR = "shaders/user"
        const val MAX_SHADERS = 32
        const val MAX_CHAIN = 16
        const val MAX_FILE_BYTES = 4 * 1024 * 1024

        private val UNSAFE_CHARS = Regex("[^A-Za-z0-9._-]+")

        /** Basename with safe characters and a shader extension. */
        fun sanitizeName(fileName: String): String {
            val base = fileName.substringAfterLast('/').substringAfterLast('\\')
                .replace(UNSAFE_CHARS, "_")
                .trim('_', '.')
                .ifEmpty { "shader" }
            val hasExtension = base.endsWith(".glsl", ignoreCase = true) ||
                base.endsWith(".hook", ignoreCase = true)
            return if (hasExtension) base else "$base.glsl"
        }
    }
}

/** Platforms without shader support (desktop's fake engine) expose an empty store. */
class NoopUserShaderStore : UserShaderStore {
    override val shaders: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override fun import(fileName: String, content: String): Result<String> =
        Result.failure(UnsupportedOperationException("Shader import is not supported on this platform"))
    override fun delete(name: String) = Unit
}
