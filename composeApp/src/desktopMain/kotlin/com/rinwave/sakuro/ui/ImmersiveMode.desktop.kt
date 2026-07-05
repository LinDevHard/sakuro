package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/** Desktop: no system bars to hide — a no-op. */
@Composable
actual fun ImmersiveMode(enabled: Boolean) = Unit
