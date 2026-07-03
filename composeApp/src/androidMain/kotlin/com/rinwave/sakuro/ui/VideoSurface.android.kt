package com.rinwave.sakuro.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine
import com.rinwave.sakuro.engine.mpv.MpvPlayerEngine
import com.rinwave.sakuro.engine.mpv.MpvScaleMode

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier) {
    when (engine) {
        is MpvPlayerEngine -> MpvVideoSurface(engine, scaleMode, modifier)

        is Media3PlayerEngine -> AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = scaleMode.toResizeMode()
                    keepScreenOn = true
                    player = engine.player
                }
            },
            update = { view ->
                view.player = engine.player
                view.resizeMode = scaleMode.toResizeMode()
            },
            modifier = modifier,
        )

        else -> FakeVideoSurface(engine, modifier)
    }
}

/**
 * mpv рендерит в обычный SurfaceView на всю площадь контейнера:
 * леттербокс (FIT), растяжение (FILL) и кроп (ZOOM) делает сам движок
 * через keepaspect/panscan — см. [MpvPlayerEngine.setScaleMode].
 */
@Composable
private fun MpvVideoSurface(engine: MpvPlayerEngine, scaleMode: ScaleMode, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                keepScreenOn = true
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            engine.attachSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            engine.resizeSurface(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            engine.detachSurface()
                        }
                    },
                )
            }
        },
        update = { engine.setScaleMode(scaleMode.toMpvScaleMode()) },
        modifier = modifier,
    )
}

private fun ScaleMode.toMpvScaleMode(): MpvScaleMode = when (this) {
    ScaleMode.FIT -> MpvScaleMode.FIT
    ScaleMode.FILL -> MpvScaleMode.FILL
    ScaleMode.ZOOM -> MpvScaleMode.ZOOM
}

@UnstableApi
private fun ScaleMode.toResizeMode(): Int = when (this) {
    ScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    ScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    ScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
