package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    // Кадр взять неоткуда (SampleVideoLibrary отдаёт fake://-URI,
    // видео-декодера в Coil на JVM нет) — карточка показывает плейсхолдер.
}
