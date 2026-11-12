package com.rinwave.sakuro.core.model

/** A minimal reference to playable media; the rich library model lives in core-media. */
data class MediaSource(
    val uri: String,
    val title: String,
)
