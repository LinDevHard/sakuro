package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * Системные регуляторы для свайп-жестов плеера (FEATURES.md §3.1):
 * яркость экрана и громкость медиа, значения нормированы в 0..1.
 */
interface PlayerSystemControls {

    val brightness: Float

    val volume: Float

    fun setBrightness(value: Float)

    fun setVolume(value: Float)
}

/**
 * Android: яркость — атрибуты окна Activity (сбрасывается при выходе с экрана),
 * громкость — AudioManager/STREAM_MUSIC. Desktop: заглушка в памяти.
 */
@Composable
expect fun rememberPlayerSystemControls(): PlayerSystemControls
