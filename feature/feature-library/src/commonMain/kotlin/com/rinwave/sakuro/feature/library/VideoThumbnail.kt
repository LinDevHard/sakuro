package com.rinwave.sakuro.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A video preview frame for the library card (docs/product/design.md §"Library").
 * Android: Coil 3 + coil-video pulls a frame from the file; desktop: no-op —
 * Coil has no video decoder on the JVM, so the card placeholder remains below.
 */
@Composable
internal expect fun VideoThumbnail(uri: String, modifier: Modifier)
