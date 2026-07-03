package com.rinwave.sakuro

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
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

        // PiP при сворачивании во время воспроизведения (FEATURES.md §3.1):
        // на 12+ система входит сама (autoEnter), раньше — onUserLeaveHint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        val builder = PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio(request))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(request?.playing == true)
        }
        return builder.build()
    }

    private fun pipAspectRatio(request: PipBridge.Request?): Rational {
        val width = request?.videoWidth ?: 0
        val height = request?.videoHeight ?: 0
        if (width <= 0 || height <= 0) return Rational(16, 9)
        // Система принимает аспект только в диапазоне 1:2.39..2.39:1.
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
    }
}
