package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * Platform video-file picking: SAF (`ACTION_OPEN_DOCUMENT`) on Android,
 * AWT FileDialog on desktop. Returns an "open the picker" lambda.
 */
@Composable
expect fun rememberVideoFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit

@Composable
expect fun rememberSubtitleFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit

/** Picks an mpv user-shader file and reads its text content. */
@Composable
expect fun rememberShaderFilePicker(onPicked: (name: String, content: String) -> Unit): () -> Unit
