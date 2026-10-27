package com.rinwave.sakuro.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rinwave.sakuro.R

@Composable
actual fun rememberDiagnosticPlatform(): String {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val gles = manager?.deviceConfigurationInfo?.glEsVersion ?: "unknown"
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
            "device: ${Build.MANUFACTURER} ${Build.MODEL}; hardware: ${Build.HARDWARE}; supported GLES: $gles"
    }
}

@Composable
actual fun rememberShareDiagnosticReport(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val share: (String) -> Unit = { report ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostic_share_report)))
        }
        share
    }
}
