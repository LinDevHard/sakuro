package com.rinwave.sakuro.feature.bench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rinwave.sakuro.core.model.GpuInfoProvider
import com.rinwave.sakuro.core.player.EngineRegistry

/** Desktop has only the fake engine — there is nothing meaningful to benchmark. */
@Composable
internal actual fun rememberBenchRunner(engineRegistry: EngineRegistry, gpuInfo: GpuInfoProvider): BenchRunner? = null

@Composable
internal actual fun BenchSurface(runner: BenchRunner, modifier: Modifier) = Unit
