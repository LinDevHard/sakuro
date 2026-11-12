package com.rinwave.sakuro.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.model.MediaSource
import com.rinwave.sakuro.core.model.ScaleMode
import com.rinwave.sakuro.core.model.SubtitlePreferences
import com.rinwave.sakuro.core.model.UpscaleProfile
import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.ExternalSubtitle
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerState
import com.rinwave.sakuro.core.player.TrackSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoSurfaceRendererTest {

    /** Selection only looks at the engine's type; no member is ever called. */
    private class StubEngine : PlayerEngine {
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
        override val debugStats: Flow<DebugStats> = emptyFlow()
        override fun load(media: MediaSource) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun selectTrack(track: TrackSelection) = Unit
        override fun addExternalSubtitle(subtitle: ExternalSubtitle) = Unit
        override fun applyUpscale(profile: UpscaleProfile) = Unit
        override fun release() = Unit
    }

    private class TestRenderer(private val accepts: (PlayerEngine) -> Boolean) : VideoSurfaceRenderer {
        override fun canRender(engine: PlayerEngine): Boolean = accepts(engine)

        @Composable
        override fun Render(
            engine: PlayerEngine,
            scaleMode: ScaleMode,
            subtitles: SubtitlePreferences,
            modifier: Modifier,
        ) = Unit
    }

    @Test
    fun picksTheFirstRendererThatAcceptsTheEngine() {
        val rejects = TestRenderer { false }
        val first = TestRenderer { it is StubEngine }
        val second = TestRenderer { true }

        assertSame(first, listOf(rejects, first, second).rendererFor(StubEngine()))
    }

    @Test
    fun returnsNullWhenNoRendererAcceptsTheEngine() {
        assertNull(listOf(TestRenderer { false }).rendererFor(StubEngine()))
    }
}
