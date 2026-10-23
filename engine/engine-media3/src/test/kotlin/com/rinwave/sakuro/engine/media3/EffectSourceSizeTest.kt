package com.rinwave.sakuro.engine.media3

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectSourceSizeTest {
    @Test
    fun `new decoded height rebuilds once and repeated notifications do not`() {
        val size = EffectSourceSize()
        size.onEffectsBuilt(720)
        assertTrue(size.needsRebuild(1080))
        size.onEffectsBuilt(1080)
        assertFalse(size.needsRebuild(1080))
        assertFalse(size.needsRebuild(0))
    }

    @Test
    fun `unknown source rebuilds when decoded size arrives`() {
        val size = EffectSourceSize()
        size.onEffectsBuilt(0)
        assertTrue(size.needsRebuild(720))
    }

    @Test
    fun `chains without source dependent sizing never rebuild on size alone`() {
        val size = EffectSourceSize()
        size.onEffectsBuilt(-1)
        assertFalse(size.needsRebuild(1080))
    }
}
