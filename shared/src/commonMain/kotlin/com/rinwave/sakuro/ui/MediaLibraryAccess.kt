package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/** Null when platform library permission is already available or is unnecessary. */
@Composable
expect fun rememberConnectMediaLibrary(): (() -> Unit)?
