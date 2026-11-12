package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.ScaleMode
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.rinwave.sakuro.engine.fake.FakePlayerEngine
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoSurfaceRendererTest {

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
        val first = TestRenderer { it is FakePlayerEngine }
        val second = TestRenderer { true }

        assertSame(first, listOf(rejects, first, second).rendererFor(FakePlayerEngine()))
    }

    @Test
    fun returnsNullWhenNoRendererAcceptsTheEngine() {
        assertNull(listOf(TestRenderer { false }).rendererFor(FakePlayerEngine()))
    }
}
