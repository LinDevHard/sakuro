package com.rinwave.sakuro.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberVideoFilePicker(onPicked: (uri: String, title: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            callback.value(uri.toString(), context.displayName(uri) ?: "English")
        }
    }

    return { launcher.launch(arrayOf("video/*")) }
}

private fun android.content.Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
