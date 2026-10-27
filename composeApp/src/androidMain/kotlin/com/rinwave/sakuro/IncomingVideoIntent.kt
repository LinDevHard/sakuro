package com.rinwave.sakuro

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.rinwave.sakuro.playback.incomingVideoUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The activity retains the sender's transient grant; persistence is optional. */
internal fun ComponentActivity.handleIncomingVideo(intent: Intent, openVideo: (String, String) -> Unit): Job? {
    if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_SEND) return null
    val uri = runCatching {
        val stream = if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toString()
        } else {
            null
        }
        incomingVideoUri(intent.action, intent.type, intent.dataString, stream)?.let(Uri::parse)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(this, R.string.incoming_video_unsupported, Toast.LENGTH_LONG).show()
        return null
    }
    return lifecycleScope.launch {
        val title = withContext(Dispatchers.IO) {
            if (uri.scheme == "content") {
                if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
                    intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
                ) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull()
            } else {
                uri.lastPathSegment
            }
        }
        openVideo(uri.toString(), title?.takeIf { it.isNotBlank() } ?: getString(R.string.incoming_video_title))
    }
}
