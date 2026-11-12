package com.rinwave.sakuro.feature.library

import androidx.compose.runtime.Composable

/** Null when platform library permission is already available or is unnecessary. */
@Composable
internal expect fun rememberConnectMediaLibrary(): (() -> Unit)?
