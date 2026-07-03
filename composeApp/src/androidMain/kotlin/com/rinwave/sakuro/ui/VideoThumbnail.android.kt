package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent

/** Доля длительности, откуда берём кадр: начало часто чёрное или с логотипами. */
private const val THUMBNAIL_FRAME_PERCENT = 0.2

@Composable
actual fun VideoThumbnail(uri: String, modifier: Modifier) {
    val request = ImageRequest.Builder(LocalPlatformContext.current)
        .data(uri)
        .videoFramePercent(THUMBNAIL_FRAME_PERCENT)
        .crossfade(true)
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
