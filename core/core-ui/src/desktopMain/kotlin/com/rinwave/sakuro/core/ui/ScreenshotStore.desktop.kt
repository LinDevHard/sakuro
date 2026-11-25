package com.rinwave.sakuro.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** The desktop app has no real video surface yet (FakePlayerEngine), so nothing to capture. */
private object UnsupportedScreenshotStore : ScreenshotStore {
    override val supported = false
    override suspend fun save(image: ImageBitmap, baseName: String): String? = null
    override fun share(uri: String) = Unit
}

@Composable
actual fun rememberScreenshotStore(): ScreenshotStore = UnsupportedScreenshotStore
