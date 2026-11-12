package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberVideoFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit {
    return rememberFilePicker("Open video", onPicked)
}

@Composable
actual fun rememberSubtitleFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit {
    return rememberFilePicker("Open subtitles", onPicked)
}

@Composable
actual fun rememberShaderFilePicker(onPicked: (name: String, content: String) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, "Open shader", FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file
        val dir = dialog.directory
        if (file != null && dir != null) {
            runCatching { callback.value(file, java.io.File(dir, file).readText()) }
        }
    }
}

@Composable
private fun rememberFilePicker(
    title: String,
    onPicked: (uri: String, title: String) -> Unit,
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file
        val dir = dialog.directory
        if (file != null && dir != null) {
            callback.value("file://$dir$file", file)
        }
    }
}
