package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Превью-кадр видео для карточки библиотеки (DESIGN.md §"Библиотека").
 * Android: Coil 3 + coil-video достаёт кадр из файла; desktop: no-op —
 * видео-декодера в Coil на JVM нет, снизу остаётся плейсхолдер карточки.
 */
@Composable
expect fun VideoThumbnail(uri: String, modifier: Modifier)
