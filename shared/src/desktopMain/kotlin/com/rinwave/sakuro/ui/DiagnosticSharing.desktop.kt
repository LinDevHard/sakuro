package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberDiagnosticPlatform(): String = remember {
    "${System.getProperty("os.name")} ${System.getProperty("os.version")}; " +
        "architecture: ${System.getProperty("os.arch")}; GPU: unavailable"
}

@Composable
actual fun rememberShareDiagnosticReport(): (String) -> Unit = remember {
    val share: (String) -> Unit = { report ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
    }
    share
}
