package com.rinwave.sakuro.core.upscale

/**
 * Встроенные пресеты под классы контента (FEATURES.md §2.2).
 * Цепочки вдохновлены режимами Anime4K A/B/C; в Media3 применяются
 * портированными GLSL ES-проходами, в libmpv — нативными `.glsl`.
 */
object BuiltInPresets {

    val OFF = UpscaleProfile(
        id = "off",
        name = "Выкл",
        description = "Оригинальная картинка без обработки",
        builtIn = true,
    )

    val ANIME_SD = UpscaleProfile(
        id = "anime-sd",
        name = "Anime SD",
        description = "2× апскейл, деноиз и агрессивная резкость для SD-аниме и слабых рипов",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Denoise(0.35f),
            UpscalePass.Upscale(2f),
            UpscalePass.Sharpen(0.8f),
        ),
        builtIn = true,
    )

    val ANIME_HD = UpscaleProfile(
        id = "anime-hd",
        name = "Anime HD",
        description = "1.5× апскейл и умеренная резкость для 720p/1080p-аниме",
        contentClass = ContentClass.ANIME,
        passes = listOf(
            UpscalePass.Upscale(1.5f),
            UpscalePass.Sharpen(0.5f),
        ),
        builtIn = true,
    )

    val LIVE_ACTION_LIGHT = UpscaleProfile(
        id = "live-light",
        name = "Live-action light",
        description = "Деликатная резкость без апскейла — для реальной съёмки",
        contentClass = ContentClass.LIVE_ACTION,
        passes = listOf(
            UpscalePass.Sharpen(0.25f),
        ),
        builtIn = true,
    )

    val all: List<UpscaleProfile> = listOf(OFF, ANIME_SD, ANIME_HD, LIVE_ACTION_LIGHT)

    fun byId(id: String?): UpscaleProfile? = all.firstOrNull { it.id == id }

    /** Авто-выбор пресета по классу контента (для core-detect, Фаза 2). */
    fun forContentClass(contentClass: ContentClass): UpscaleProfile = when (contentClass) {
        ContentClass.ANIME -> ANIME_HD
        ContentClass.CARTOON -> ANIME_HD
        ContentClass.LIVE_ACTION -> LIVE_ACTION_LIGHT
        ContentClass.UNKNOWN -> OFF
    }
}
