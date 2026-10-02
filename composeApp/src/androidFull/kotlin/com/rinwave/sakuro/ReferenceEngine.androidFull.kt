package com.rinwave.sakuro

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.engine.mpv.MpvEngineFactory
import com.rinwave.sakuro.engine.mpv.MpvPlayerEngine
import com.rinwave.sakuro.engine.mpv.MpvScaleMode
import com.rinwave.sakuro.ui.ScaleMode

internal fun referenceEngineFactories(context: Context): List<PlayerEngineFactory> =
    listOf(MpvEngineFactory(context))

/** Render the optional libmpv reference engine; return false for product engines. */
@Composable
internal fun referenceVideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier): Boolean {
    if (engine !is MpvPlayerEngine) return false
    AndroidView(
        factory = {
            SurfaceView(it).apply {
                keepScreenOn = true
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = engine.attachSurface(holder.surface)

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            engine.resizeSurface(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachSurface()
                    },
                )
            }
        },
        update = { engine.setScaleMode(scaleMode.toMpvScaleMode()) },
        modifier = modifier,
    )
    return true
}

internal fun attachReferenceEngineSurface(engine: PlayerEngine, view: SurfaceView): Boolean {
    if (engine !is MpvPlayerEngine) return false
    engine.attachSurface(view.holder.surface)
    engine.resizeSurface(1920, 1080)
    engine.setExactSeeking(true)
    return true
}

internal fun detachReferenceEngineSurface(engine: PlayerEngine) {
    (engine as? MpvPlayerEngine)?.detachSurface()
}

private fun ScaleMode.toMpvScaleMode(): MpvScaleMode = when (this) {
    ScaleMode.FIT -> MpvScaleMode.FIT
    ScaleMode.FILL -> MpvScaleMode.FILL
    ScaleMode.ZOOM -> MpvScaleMode.ZOOM
}
