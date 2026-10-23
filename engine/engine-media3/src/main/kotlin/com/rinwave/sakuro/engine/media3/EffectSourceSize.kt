package com.rinwave.sakuro.engine.media3

/** Source height used to build absolute Presentation sizes; -1 means source-independent. */
internal class EffectSourceSize {
    private var builtHeight = -1

    fun onEffectsBuilt(sourceHeight: Int) {
        builtHeight = sourceHeight
    }

    fun needsRebuild(sourceHeight: Int): Boolean =
        builtHeight >= 0 && sourceHeight > 0 && builtHeight != sourceHeight
}
