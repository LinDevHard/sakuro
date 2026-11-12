package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A video preview frame for the library card (DESIGN.md §"Library").
 * Android: Coil 3 + coil-video pulls a frame from the file; desktop: no-op —
 * Coil has no video decoder on the JVM, so the card placeholder remains below.
 */
@Composable
expect fun VideoThumbnail(uri: String, modifier: Modifier)
