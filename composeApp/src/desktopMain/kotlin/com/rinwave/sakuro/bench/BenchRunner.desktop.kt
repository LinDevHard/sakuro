package com.rinwave.sakuro.bench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.player.EngineRegistry

/** Desktop has only the fake engine — there is nothing meaningful to benchmark. */
@Composable
actual fun rememberBenchRunner(engineRegistry: EngineRegistry): BenchRunner? = null

@Composable
actual fun BenchSurface(runner: BenchRunner, modifier: Modifier) = Unit
