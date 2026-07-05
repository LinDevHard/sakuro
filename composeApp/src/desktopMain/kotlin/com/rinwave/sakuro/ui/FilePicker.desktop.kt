package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberVideoFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, "Open video", FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file
        val dir = dialog.directory
        if (file != null && dir != null) {
            callback.value("file://$dir$file", file)
        }
    }
}
