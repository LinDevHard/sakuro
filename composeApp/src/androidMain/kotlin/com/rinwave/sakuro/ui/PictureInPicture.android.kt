package com.rinwave.sakuro.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Player → [com.rinwave.sakuro.MainActivity] bridge: the activity subscribes
 * to [request] (updates PictureInPictureParams / enters PiP from
 * onUserLeaveHint) and writes to [isInPip] from onPictureInPictureModeChanged.
 */
object PipBridge {

    const val ACTION_PLAY_PAUSE = "com.rinwave.sakuro.action.PIP_PLAY_PAUSE"

    /** Player state for PiP; null — the player screen is not active and PiP is disallowed. */
    data class Request(val playing: Boolean, val videoWidth: Int, val videoHeight: Int)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    private val _isInPip = MutableStateFlow(false)
    val isInPip: StateFlow<Boolean> = _isInPip.asStateFlow()

    private val _actions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actions: SharedFlow<String> = _actions

    fun update(request: Request?) {
        _request.value = request
    }

    fun onPipModeChanged(inPip: Boolean) {
        _isInPip.value = inPip
    }

    fun sendAction(action: String) {
        _actions.tryEmit(action)
    }
}

@Composable
actual fun PipEffect(
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
) {
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnPause by rememberUpdatedState(onPause)
    LaunchedEffect(isPlaying, videoWidth, videoHeight) {
        PipBridge.update(PipBridge.Request(isPlaying, videoWidth, videoHeight))
    }
    LaunchedEffect(Unit) {
        PipBridge.actions.collect { action ->
            if (action == PipBridge.ACTION_PLAY_PAUSE) {
                if (currentIsPlaying) currentOnPause() else currentOnPlay()
            }
        }
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

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PipBridge.sendAction(intent.action ?: return)
    }
}
