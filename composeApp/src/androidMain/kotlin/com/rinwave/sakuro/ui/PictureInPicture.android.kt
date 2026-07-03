package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мост плеер → [com.rinwave.sakuro.MainActivity]: активити подписывается
 * на [request] (обновляет PictureInPictureParams / входит в PiP из
 * onUserLeaveHint) и пишет в [isInPip] из onPictureInPictureModeChanged.
 */
object PipBridge {

    /** Состояние плеера для PiP; null — экран плеера не активен, PiP запрещён. */
    data class Request(val playing: Boolean, val videoWidth: Int, val videoHeight: Int)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    fun update(request: Request?) {
        _request.value = request
    }

    fun onPipModeChanged(inPip: Boolean) {
        _isInPip.value = inPip
    }
}

@Composable
actual fun PipEffect(isPlaying: Boolean, videoWidth: Int, videoHeight: Int) {
    LaunchedEffect(isPlaying, videoWidth, videoHeight) {
        PipBridge.update(PipBridge.Request(isPlaying, videoWidth, videoHeight))
    }
    DisposableEffect(Unit) {
        onDispose { PipBridge.update(null) }
    }
}

@Composable
actual fun rememberIsInPip(): Boolean {
    val inPip by PipBridge.isInPip.collectAsState()
    return inPip
}

actual fun isInPipNow(): Boolean = PipBridge.isInPip.value
