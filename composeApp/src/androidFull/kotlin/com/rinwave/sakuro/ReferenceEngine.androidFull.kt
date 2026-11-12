package com.rinwave.sakuro

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.core.player.ScaleMode
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.engine.mpv.MpvEngineFactory
import com.rinwave.sakuro.engine.mpv.MpvPlayerEngine
import com.rinwave.sakuro.engine.mpv.MpvScaleMode
import com.rinwave.sakuro.ui.VideoSurfaceRenderer

internal fun referenceEngineFactories(context: Context): List<PlayerEngineFactory> =
    listOf(MpvEngineFactory(context))

internal fun referenceSurfaceRenderers(): List<VideoSurfaceRenderer> = listOf(MpvSurfaceRenderer())

/** libmpv draws into a plain SurfaceView; mpv renders subtitles itself. */
private class MpvSurfaceRenderer : VideoSurfaceRenderer {

    override fun canRender(engine: PlayerEngine): Boolean = engine is MpvPlayerEngine

    @Composable
    override fun Render(
        engine: PlayerEngine,
        scaleMode: ScaleMode,
        subtitles: SubtitlePreferences,
        modifier: Modifier,
    ) {
        val mpv = engine as MpvPlayerEngine
        AndroidView(
            factory = {
                SurfaceView(it).apply {
                    keepScreenOn = true
                    holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = mpv.attachSurface(holder)

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                mpv.resizeSurface(width, height)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) = mpv.detachSurface()
                        },
                    )
                }
            },
            update = { mpv.setScaleMode(scaleMode.toMpvScaleMode()) },
            modifier = modifier,
        )
    }
}

private fun ScaleMode.toMpvScaleMode(): MpvScaleMode = when (this) {
    ScaleMode.FIT -> MpvScaleMode.FIT
    ScaleMode.FILL -> MpvScaleMode.FILL
    ScaleMode.ZOOM -> MpvScaleMode.ZOOM
}
