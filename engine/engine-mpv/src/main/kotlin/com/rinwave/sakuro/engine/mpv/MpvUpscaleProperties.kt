package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.math.roundToInt

/**
 * Перевод абстрактной цепочки [UpscalePass] в свойства mpv (ARCHITECTURE.md §4).
 * mpv всегда масштабирует к размеру surface, поэтому фактор Upscale-прохода
 * не нужен — от него остаётся выбор качества скейлера. Все свойства
 * runtime-изменяемые: применение пресета не требует re-prepare, в отличие от Media3.
 *
 * Denoise-проход деградирует (контракт [UpscaleProfile]): в prebuilt libmpv
 * ffmpeg собран без денойз-фильтров (в libavfilter.so нет hqdn3d/nlmeans/
 * atadenoise), а vf-граф с неизвестным фильтром отключает видео-дорожку целиком.
 */
internal fun buildMpvUpscaleProperties(profile: UpscaleProfile): List<Pair<String, String>> {
    var scale = "bilinear"
    var sharpen = 0f
    for (pass in profile.passes) {
        when (pass) {
            is UpscalePass.Upscale -> scale = "ewa_lanczossharp"
            is UpscalePass.Sharpen -> sharpen = pass.strength
            is UpscalePass.Denoise -> Unit
        }
    }
    return listOf(
        "scale" to scale,
        "cscale" to scale,
        "sharpen" to sharpen.fmt(),
    )
}

/** Float → строка с 2 знаками и точкой-разделителем независимо от локали. */
private fun Float.fmt(): String = ((this * 100).roundToInt() / 100.0).toString()
