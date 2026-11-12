package com.rinwave.sakuro.feature.player

import androidx.compose.runtime.Composable

/** Desktop: no system bars to hide — a no-op. */
@Composable
internal actual fun ImmersiveMode(enabled: Boolean) = Unit
