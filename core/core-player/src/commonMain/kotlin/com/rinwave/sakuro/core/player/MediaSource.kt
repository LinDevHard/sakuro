package com.rinwave.sakuro.core.player

/** Минимальная ссылка на воспроизводимое медиа; богатая модель библиотеки живёт в core-media. */
data class MediaSource(
    val uri: String,
    val title: String,
)
