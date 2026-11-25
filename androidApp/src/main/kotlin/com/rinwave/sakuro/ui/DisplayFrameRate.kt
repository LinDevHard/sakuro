package com.rinwave.sakuro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.model.FrameRateMatching
import kotlin.math.abs
import kotlin.math.roundToInt

/** A display mode as far as frame-rate matching cares. */
internal data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

internal object DisplayModePicker {
    private const val TOLERANCE = 0.005f

    /**
     * The smallest whole multiple of [fps] the panel offers at its current resolution
     * (24p → 24/48/72/120 Hz, 25p → 50/100 Hz), the closest match first so 24p takes a true
     * 24 Hz mode over 23.976 Hz. Null when the panel has none.
     */
    fun pick(modes: List<DisplayModeInfo>, current: DisplayModeInfo, fps: Float): DisplayModeInfo? {
        if (fps <= 0f) return null
        return modes
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { mode ->
                val multiple = mode.refreshRate / fps
                val whole = multiple.roundToInt()
                val error = abs(multiple - whole) / whole
                if (whole >= 1 && error <= TOLERANCE) Triple(mode, whole, error) else null
            }
            .minWithOrNull(compareBy<Triple<DisplayModeInfo, Int, Float>> { it.second }.thenBy { it.third })
            ?.first
    }
}

/**
 * Votes for a display refresh rate that matches the video, on the visible [PlayerView] surface.
 *
 * - Android 11+: `Surface.setFrameRate` with the fixed-source compatibility; on 12+ the
 *   "always" choice may also switch non-seamlessly (a brief blank, like a TV changing mode).
 * - Android 8–11 with "always": selects a matching display mode for the window.
 *
 * Media3's own hint is disabled in the engine: with video effects its frames go to the effect
 * graph's surface, not this one.
 */
@OptIn(UnstableApi::class)
internal class DisplayFrameRateController(private val view: PlayerView) : SurfaceHolder.Callback {

    private var fps = 0f
    private var mode = FrameRateMatching.SEAMLESS
    private val holder: SurfaceHolder? = (view.videoSurfaceView as? SurfaceView)?.holder

    init {
        holder?.addCallback(this)
    }

    fun update(fps: Float, mode: FrameRateMatching) {
        if (fps == this.fps && mode == this.mode) return
        this.fps = fps
        this.mode = mode
        apply()
    }

    fun release() {
        fps = 0f
        mode = FrameRateMatching.OFF
        apply()
        holder?.removeCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = apply()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun apply() {
        val target = if (mode == FrameRateMatching.OFF) 0f else fps
        holder?.surface?.takeIf { it.isValid }?.let { surface -> runCatching { voteOnSurface(surface, target) } }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) applyWindowMode()
    }

    private fun voteOnSurface(surface: Surface, target: Float) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> surface.setFrameRate(
                target,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                if (mode == FrameRateMatching.ALWAYS) {
                    Surface.CHANGE_FRAME_RATE_ALWAYS
                } else {
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                },
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                surface.setFrameRate(target, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }

    /** Before Android 12 a non-seamless switch has to be requested as a window display mode. */
    private fun applyWindowMode() {
        val window = view.context.findActivity()?.window ?: return
        val display = view.display ?: return
        val modeId = if (mode == FrameRateMatching.ALWAYS && fps > 0f) {
            val current = display.mode.toInfo()
            DisplayModePicker.pick(display.supportedModes.map { it.toInfo() }, current, fps)?.id ?: 0
        } else {
            0
        }
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        window.attributes = attributes
    }

    private fun android.view.Display.Mode.toInfo() =
        DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
