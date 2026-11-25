package com.rinwave.sakuro.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lets the player screen ask the visible video surface for its current frame. The engine's
 * [VideoSurfaceRenderer] registers a capture while it is on screen; the frame is what the
 * viewer sees (after upscaling, at the surface's resolution), without subtitles or controls.
 */
class VideoSnapshotter {
    private var source: (suspend () -> ImageBitmap?)? by mutableStateOf(null)

    /** A surface that can be captured is on screen. */
    val available: Boolean get() = source != null

    /** Returns the unregister action for the renderer's dispose. */
    fun register(capture: suspend () -> ImageBitmap?): () -> Unit {
        source = capture
        return { if (source === capture) source = null }
    }

    suspend fun capture(): ImageBitmap? = source?.invoke()
}

val LocalVideoSnapshotter = staticCompositionLocalOf<VideoSnapshotter?> { null }

/** Saves frames where the platform's photo apps find them and shares them. */
interface ScreenshotStore {
    val supported: Boolean

    /** The saved image's URI, or null when saving failed (or storage permission was denied). */
    suspend fun save(image: ImageBitmap, baseName: String): String?

    fun share(uri: String)
}

@Composable
expect fun rememberScreenshotStore(): ScreenshotStore
