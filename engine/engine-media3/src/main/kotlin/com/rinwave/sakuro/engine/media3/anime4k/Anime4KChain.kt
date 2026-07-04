package com.rinwave.sakuro.engine.media3.anime4k

import android.content.Context
import android.util.Log
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import java.io.IOException

/**
 * Выбор и загрузка цепочки Anime4K `.glsl` под [UpscaleProfile] для движка Media3.
 *
 * Порядок и выбор моделей S/M повторяют движок mpv
 * (`MpvUpscaleProperties.buildAnime4kChain`, docs/anime4k-media3-port-plan.md §3.5):
 * канонический порядок Clamp→Denoise→Restore→Upscale, размер CNN по силе прохода.
 * Ассеты берутся из `assets/anime4k/` (их вендорит модуль engine-mpv; в собранном
 * приложении ассеты модулей смёрджены в один [android.content.res.AssetManager]).
 */
internal object Anime4KChain {

    private const val ASSET_DIR = "anime4k"

    /** Порог силы Sharpen, с которого берётся средняя CNN-модель вместо малой. */
    private const val HEAVY_SHARPEN = 0.6f

    /** Порог фактора Upscale, с которого берётся средняя CNN-модель вместо малой. */
    private const val HEAVY_UPSCALE = 1.75f

    /** Имена `.glsl`-файлов цепочки для профиля (пусто — Anime4K не применим). */
    fun shaderFilesFor(profile: UpscaleProfile): List<String> {
        val anime = profile.contentClass == ContentClass.ANIME || profile.contentClass == ContentClass.CARTOON
        if (!anime || profile.passes.isEmpty()) return emptyList()
        val chain = buildList {
            profile.passes.filterIsInstance<UpscalePass.Denoise>().firstOrNull()?.let {
                add("Anime4K_Denoise_Bilateral_Mode.glsl")
            }
            profile.passes.filterIsInstance<UpscalePass.Sharpen>().firstOrNull()?.let {
                add(if (it.strength >= HEAVY_SHARPEN) "Anime4K_Restore_CNN_M.glsl" else "Anime4K_Restore_CNN_S.glsl")
            }
            profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.let {
                val heavy = it.factor >= HEAVY_UPSCALE
                add(if (heavy) "Anime4K_Upscale_CNN_x2_M.glsl" else "Anime4K_Upscale_CNN_x2_S.glsl")
            }
        }
        if (chain.isEmpty()) return emptyList()
        // Кламп подсветки защищает CNN-проходы от рингинга на пересвеченных линиях.
        return listOf("Anime4K_Clamp_Highlights.glsl") + chain
    }

    /**
     * Загружает и парсит цепочку для профиля в плоский список проходов.
     * Проходы файлов конкатенируются в порядке [shaderFilesFor]. Пустой список —
     * Anime4K не применяется (профиль не аниме/мультик или без проходов).
     */
    fun load(context: Context, profile: UpscaleProfile): List<UserShaderPass> {
        val files = shaderFilesFor(profile)
        if (files.isEmpty()) return emptyList()
        return try {
            files.flatMap { file ->
                val source = context.assets.open("$ASSET_DIR/$file").bufferedReader().use { it.readText() }
                MpvUserShaderParser.parse(source)
            }
        } catch (e: IOException) {
            // Ассеты Anime4K не в этой сборке (нет модуля engine-mpv) — откат на legacy-цепочку.
            Log.w(TAG, "Anime4K assets недоступны, откат на legacy: ${e.message}")
            emptyList()
        }
    }

    private const val TAG = "Anime4KChain"
}
