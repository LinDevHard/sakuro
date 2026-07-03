package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/** Desktop: системного PiP нет — обычное окно и так «поверх». */
@Composable
actual fun PipEffect(isPlaying: Boolean, videoWidth: Int, videoHeight: Int) = Unit

@Composable
actual fun rememberIsInPip(): Boolean = false

actual fun isInPipNow(): Boolean = false
