package com.rinwave.sakuro.core.player

/** A minimal reference to playable media; the rich library model lives in core-media. */
data class MediaSource(
    val uri: String,
    val title: String,
)
