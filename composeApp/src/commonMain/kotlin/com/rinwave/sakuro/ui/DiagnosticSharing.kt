package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/** Device/OS descriptors only; never includes media or user account information. */
@Composable
expect fun rememberDiagnosticPlatform(): String

/** Caller must show the report preview before offering this explicit action. */
@Composable
expect fun rememberShareDiagnosticReport(): (String) -> Unit
