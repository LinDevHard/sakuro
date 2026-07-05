package com.rinwave.sakuro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@Composable
actual fun rememberPlayerSystemControls(): PlayerSystemControls {
    val context = LocalContext.current
    val controls = remember(context) { AndroidPlayerSystemControls(context) }
    DisposableEffect(controls) {
        onDispose { controls.resetBrightness() }
    }
    return controls
}

private class AndroidPlayerSystemControls(context: Context) : PlayerSystemControls {

    private val activity: Activity? = context.findActivity()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    override val brightness: Float
        get() {
            val current = activity?.window?.attributes?.screenBrightness ?: return DEFAULT_BRIGHTNESS
            return if (current >= 0f) current else DEFAULT_BRIGHTNESS
        }

    override val volume: Float
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    override fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f)
        }
    }

    override fun setVolume(value: Float) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * maxVolume).roundToInt(),
            0,
        )
    }

    /** Restores system brightness when leaving the player screen. */
    fun resetBrightness() {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private companion object {
        // Zero brightness looks like a powered-off screen — we keep a lower bound.
        const val MIN_BRIGHTNESS = 0.01f
        const val DEFAULT_BRIGHTNESS = 0.5f
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
