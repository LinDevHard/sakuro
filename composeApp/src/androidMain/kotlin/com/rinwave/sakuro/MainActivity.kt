package com.rinwave.sakuro

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.defaultComponentContext
import com.rinwave.sakuro.core.media.LibraryEvents
import com.rinwave.sakuro.di.AppDependencies
import com.rinwave.sakuro.navigation.RootComponent
import com.rinwave.sakuro.ui.PipActionReceiver
import com.rinwave.sakuro.ui.PipBridge
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    private val requestMediaPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) LibraryEvents.requestRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deps = GlobalContext.get().get<AppDependencies>()
        val root = RootComponent(defaultComponentContext(), deps)

        setContent {
            App(root)
        }

        requestMediaPermissionIfNeeded()

        // PiP when minimized during playback (FEATURES.md §3.1):
        // on 12+ the system enters it itself (autoEnter), earlier — onUserLeaveHint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                PipBridge.request.collect { setPictureInPictureParams(buildPipParams(it)) }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val request = PipBridge.request.value
            if (request?.playing == true) {
                enterPictureInPictureMode(buildPipParams(request))
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipBridge.onPipModeChanged(isInPictureInPictureMode)
    }

    private fun buildPipParams(request: PipBridge.Request?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspectRatio(request))
            .setActions(buildPipActions(request))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(request?.playing == true)
        }
        return builder.build()
    }

    private fun buildPipActions(request: PipBridge.Request?): List<RemoteAction> {
        request ?: return emptyList()
        val iconRes = if (request.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = getString(if (request.playing) R.string.pip_pause else R.string.pip_play)
        val intent = Intent(this, PipActionReceiver::class.java).setAction(PipBridge.ACTION_PLAY_PAUSE)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            PIP_ACTION_PLAY_PAUSE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return listOf(
            RemoteAction(
                Icon.createWithResource(this, iconRes),
                title,
                title,
                pendingIntent,
            ),
        )
    }

    private fun pipAspectRatio(request: PipBridge.Request?): Rational {
        val width = request?.videoWidth ?: 0
        val height = request?.videoHeight ?: 0
        if (width <= 0 || height <= 0) return Rational(16, 9)
        // The system only accepts an aspect within 1:2.39..2.39:1.
        val ratio = width.toFloat() / height
        return when {
            ratio > MAX_PIP_RATIO -> Rational(239, 100)
            ratio < 1f / MAX_PIP_RATIO -> Rational(100, 239)
            else -> Rational(width, height)
        }
    }

    private fun requestMediaPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestMediaPermission.launch(permission)
        }
    }

    private companion object {
        const val MAX_PIP_RATIO = 2.39f
        const val PIP_ACTION_PLAY_PAUSE_REQUEST_CODE = 4001
    }
}
