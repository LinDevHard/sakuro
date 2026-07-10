package com.rinwave.sakuro.di

import android.content.Context
import com.rinwave.sakuro.core.upscale.UserShaderStore
import com.rinwave.sakuro.engine.media3.usershader.UserShaderValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Imported user shaders under `filesDir/shaders/user` — the directory both
 * engines read ([UserShaderStore.RELATIVE_DIR]). Import validates the source
 * with the Media3 runtime so a broken file is rejected with a reason instead
 * of silently passing through at playback.
 */
class AndroidUserShaderStore(context: Context) : UserShaderStore {

    private val dir = File(context.filesDir, UserShaderStore.RELATIVE_DIR)
    private val _shaders = MutableStateFlow(list())

    override val shaders: StateFlow<List<String>> = _shaders.asStateFlow()

    override fun import(fileName: String, content: String): Result<String> {
        if (content.length > UserShaderStore.MAX_FILE_BYTES) {
            return Result.failure(IllegalArgumentException("File is too large"))
        }
        if (list().size >= UserShaderStore.MAX_SHADERS) {
            return Result.failure(IllegalArgumentException("Too many shaders (max ${UserShaderStore.MAX_SHADERS})"))
        }
        UserShaderValidator.validate(content)?.let { problem ->
            return Result.failure(IllegalArgumentException(problem))
        }
        val name = UserShaderStore.sanitizeName(fileName)
        return runCatching {
            dir.mkdirs()
            // Atomic replace: never leave a half-written shader for the engines.
            val staging = File(dir, "$name.tmp")
            staging.writeText(content)
            val target = File(dir, name)
            check(staging.renameTo(target) || (target.delete() && staging.renameTo(target))) {
                "could not store $name"
            }
            name
        }.also { _shaders.value = list() }
    }

    override fun delete(name: String) {
        File(dir, UserShaderStore.sanitizeName(name)).delete()
        _shaders.value = list()
    }

    private fun list(): List<String> = dir.listFiles()
        .orEmpty()
        .filter { it.isFile && !it.name.endsWith(".tmp") }
        .map { it.name }
        .sorted()
}
