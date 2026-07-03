package com.rinwave.sakuro.engine.mpv

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Выдаёт mpv абсолютные пути к вендоренным Anime4K-шейдерам
 * (assets/anime4k, MIT, licenses/anime4k). libmpv читает `glsl-shaders`
 * только с файловой системы, поэтому ассеты при первом обращении
 * копируются в filesDir; каталог версионирован — смена версии шейдеров
 * в ассетах приводит к новой копии, старые версии удаляются.
 */
internal class MpvShaderStore(private val context: Context) {

    private val dir: File? by lazy { provision() }

    /** Пути в порядке цепочки; null — копия не удалась, шейдеры недоступны. */
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
    }.onFailure { Log.w(TAG, "Не удалось развернуть шейдеры", it) }.getOrNull()

    private companion object {
        const val TAG = "MpvShaderStore"
        const val ASSET_DIR = "anime4k"
        const val STORE_DIR = "shaders/anime4k"

        /** Версия вендоренного набора; поднимать при обновлении ассетов. */
        const val VERSION = "v4.0.1"
    }
}
