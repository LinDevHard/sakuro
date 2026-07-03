package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.math.roundToInt

/**
 * Конфигурация рендера mpv для пресета: свойства + цепочка user-shaders.
 * [shaders] — имена файлов из assets/anime4k; в свойство `glsl-shaders`
 * движок подставляет абсолютные пути (см. [MpvShaderStore]).
 */
internal data class MpvRenderConfig(
    val properties: List<Pair<String, String>>,
    val shaders: List<String>,
)

/**
 * Перевод абстрактной цепочки [UpscalePass] в конфигурацию mpv (ARCHITECTURE.md §4).
 *
 * Для аниме/мультипликации проходы транслируются в родные Anime4K `.glsl`
 * user-shaders (вкл. Denoise — в бандл-ffmpeg денойз-фильтров нет, а шейдеру
 * libavfilter не нужен). Порядок цепочки канонический для Anime4K
 * (Clamp → Denoise → Restore → Upscale), а не порядок проходов пресета.
 * Размер CNN (S/M) выбирается по силе прохода.
 *
 * Для остального контента Anime4K не подходит по назначению — остаются
 * свойства mpv: Upscale → качественный скейлер (mpv всегда масштабирует
 * к размеру surface, фактор не нужен), Sharpen → `sharpen`, Denoise
 * деградирует (контракт [UpscaleProfile]).
 *
 * Всё применяется на лету, без re-prepare — в отличие от Media3.
 */
internal fun buildMpvRenderConfig(profile: UpscaleProfile): MpvRenderConfig {
    val shaders = buildAnime4kChain(profile)
    var scale = "bilinear"
    var sharpen = 0f
    for (pass in profile.passes) {
        when (pass) {
            is UpscalePass.Upscale -> scale = "ewa_lanczossharp"
            // Резкость аниме делает Restore_CNN — свойство продублировало бы эффект.
            is UpscalePass.Sharpen -> if (shaders.isEmpty()) sharpen = pass.strength
            is UpscalePass.Denoise -> Unit
        }
    }
    return MpvRenderConfig(
        properties = listOf(
            "scale" to scale,
            "cscale" to scale,
            "sharpen" to sharpen.fmt(),
        ),
        shaders = shaders,
    )
}

private fun buildAnime4kChain(profile: UpscaleProfile): List<String> {
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
            add(if (it.factor >= HEAVY_UPSCALE) "Anime4K_Upscale_CNN_x2_M.glsl" else "Anime4K_Upscale_CNN_x2_S.glsl")
        }
    }
    if (chain.isEmpty()) return emptyList()
    // Кламп подсветки защищает CNN-проходы от рингинга на пересвеченных линиях.
    return listOf("Anime4K_Clamp_Highlights.glsl") + chain
}

/** Порог силы Sharpen, с которого берётся средняя CNN-модель вместо малой. */
private const val HEAVY_SHARPEN = 0.6f

/** Порог фактора Upscale, с которого берётся средняя CNN-модель вместо малой. */
private const val HEAVY_UPSCALE = 1.75f

/** Float → строка с 2 знаками и точкой-разделителем независимо от локали. */
private fun Float.fmt(): String = ((this * 100).roundToInt() / 100.0).toString()
