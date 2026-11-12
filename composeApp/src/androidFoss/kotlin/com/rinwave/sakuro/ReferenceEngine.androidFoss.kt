package com.rinwave.sakuro

import android.content.Context
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.ui.VideoSurfaceRenderer

/** F-Droid builds deliberately contain no prebuilt libmpv runtime. */
@Suppress("UnusedParameter")
internal fun referenceEngineFactories(context: Context): List<PlayerEngineFactory> = emptyList()

internal fun referenceSurfaceRenderers(): List<VideoSurfaceRenderer> = emptyList()
