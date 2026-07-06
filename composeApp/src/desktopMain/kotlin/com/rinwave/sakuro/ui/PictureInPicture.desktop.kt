package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/** Desktop: there is no system PiP — a normal window is "on top" anyway. */
@Composable
actual fun PipEffect(
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
) = Unit

@Composable
actual fun rememberIsInPip(): Boolean = false

actual fun isInPipNow(): Boolean = false
