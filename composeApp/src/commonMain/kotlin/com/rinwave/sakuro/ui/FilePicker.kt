package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * Платформенный выбор видеофайла: SAF (`ACTION_OPEN_DOCUMENT`) на Android,
 * AWT FileDialog на desktop. Возвращает лямбду «открыть пикер».
 */
@Composable
expect fun rememberVideoFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit
