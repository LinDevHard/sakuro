package com.rinwave.sakuro.ui.util

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "${minutes}:${seconds.pad()}"
    }
}

private fun Long.pad(): String = toString().padStart(2, '0')

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "${((bytes.toDouble() / (1L shl 30)) * 10).toLong() / 10.0} ГБ"
    bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} МБ"
    bytes > 0 -> "${bytes / (1L shl 10)} КБ"
    else -> ""
}
