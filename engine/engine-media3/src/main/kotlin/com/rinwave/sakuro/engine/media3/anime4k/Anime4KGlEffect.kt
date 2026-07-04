package com.rinwave.sakuro.engine.media3.anime4k

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Единый [GlEffect] Anime4K для Media3: прячет весь многопроходный рендер-граф
 * mpv user-shaders за одним эффектом (docs/anime4k-media3-port-plan.md §2, §4).
 *
 * [passes] — уже распарсенные проходы всей цепочки пресета
 * (Clamp→Denoise→Restore→Upscale), склеенные по порядку из нескольких `.glsl`.
 * Пустой список делает эффект no-op.
 */
@UnstableApi
internal class Anime4KGlEffect(private val passes: List<UserShaderPass>) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4KShaderProgram(passes)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = passes.isEmpty()
}
