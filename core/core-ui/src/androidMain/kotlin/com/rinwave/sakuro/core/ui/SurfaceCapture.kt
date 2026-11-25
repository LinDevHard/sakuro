package com.rinwave.sakuro.core.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Copies what the surface currently shows: for Media3 the output of the effect (upscale) chain,
 * for mpv its rendered frame. Null when the surface is gone or has no frame yet.
 */
suspend fun SurfaceView.captureFrame(): Bitmap? {
    val surface = holder.surface
    val hasSize = width > 0 && height > 0
    if (!hasSize || surface == null || !surface.isValid) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(this, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            bitmap.recycle()
            continuation.resume(null)
        }
    }
}
