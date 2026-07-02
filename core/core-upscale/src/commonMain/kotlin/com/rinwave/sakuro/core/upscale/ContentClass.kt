package com.rinwave.sakuro.core.upscale

import kotlinx.serialization.Serializable

/**
 * Стиль изображения — первичный класс для выбора пресета (FEATURES.md §1.1).
 * «Movie» моделируется как [LIVE_ACTION] + film-флаги, отдельного класса нет.
 */
@Serializable
enum class ContentClass {
    ANIME,
    CARTOON,
    LIVE_ACTION,
    UNKNOWN,
}
