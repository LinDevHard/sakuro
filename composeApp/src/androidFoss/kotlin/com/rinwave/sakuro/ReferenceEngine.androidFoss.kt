package com.rinwave.sakuro

import android.content.Context
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.PlayerEngine
import com.rinwave.sakuro.core.player.PlayerEngineFactory
import com.rinwave.sakuro.ui.ScaleMode

/** F-Droid builds deliberately contain no prebuilt libmpv runtime. */
@Suppress("UnusedParameter")
internal fun referenceEngineFactories(context: Context): List<PlayerEngineFactory> = emptyList()

@Composable
@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun referenceVideoSurface(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier): Boolean = false

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
internal fun attachReferenceEngineSurface(engine: PlayerEngine, view: SurfaceView): Boolean = false

@Suppress("UnusedParameter")
internal fun detachReferenceEngineSurface(engine: PlayerEngine) = Unit
