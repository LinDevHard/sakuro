package com.rinwave.sakuro.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.engine.media3.Media3PlayerEngine

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    when (engine) {
        is Media3PlayerEngine -> AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    player = engine.player
                }
            },
            update = { view -> view.player = engine.player },
            modifier = modifier,
        )

        else -> FakeVideoSurface(engine, modifier)
    }
}
