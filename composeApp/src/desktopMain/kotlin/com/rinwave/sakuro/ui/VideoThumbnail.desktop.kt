package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    // There is nowhere to get a frame (SampleVideoLibrary returns fake:// URIs,
    // Coil has no video decoder on the JVM) — the card shows a placeholder.
}
