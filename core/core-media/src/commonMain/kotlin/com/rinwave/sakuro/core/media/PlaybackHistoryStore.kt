package com.rinwave.sakuro.core.media

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackProgress(val positionMs: Long, val durationMs: Long) {
    val fraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    /** Start fresh within five seconds of either edge, or after watching 95 percent. */
    val resumePositionMs: Long
        get() = if (startsFresh()) {
            0
        } else {
            positionMs
        }

    private fun startsFresh(): Boolean =
        durationMs <= 0 || positionMs <= RESUME_EDGE_MS ||
            positionMs >= durationMs - RESUME_EDGE_MS || fraction >= RESUME_COMPLETE_FRACTION

    private companion object {
        const val RESUME_EDGE_MS = 5_000L
        const val RESUME_COMPLETE_FRACTION = 0.95f
    }
}

/** Bounded local history. Call mutations serially; callers throttle periodic playback updates. */
class PlaybackHistoryStore(private val settings: Settings = Settings()) {
    // StateFlow conflates equal maps even when their retention order changes.
    private var persistedEntries = load()
    private val mutableEntries = MutableStateFlow(persistedEntries)
    val entries: StateFlow<Map<String, PlaybackProgress>> = mutableEntries.asStateFlow()
    private val mutableAutoPlayNext = MutableStateFlow(
        runCatching { settings.getBoolean(KEY_AUTO_PLAY_NEXT, false) }.getOrDefault(false),
    )
    val autoPlayNext: StateFlow<Boolean> = mutableAutoPlayNext.asStateFlow()

    fun entry(uri: String): PlaybackProgress? = entries.value[uri]

    fun record(uri: String, positionMs: Long, durationMs: Long) {
        if (uri.isBlank() || positionMs < 0 || durationMs <= 0) return
        val updated = persistedEntries.toMutableMap()
        updated.remove(uri)
        updated[uri] = PlaybackProgress(positionMs.coerceAtMost(durationMs), durationMs)
        while (updated.size > MAX_ENTRIES) updated.remove(updated.keys.first())
        save(updated)
    }

    fun clear(uri: String) = save(persistedEntries - uri)

    fun clearAll() = save(emptyMap())

    fun setAutoPlayNext(enabled: Boolean) {
        settings.putBoolean(KEY_AUTO_PLAY_NEXT, enabled)
        mutableAutoPlayNext.value = enabled
    }

    private fun save(values: Map<String, PlaybackProgress>) {
        settings.putString(
            KEY_HISTORY,
            values.entries.joinToString("\n") { (uri, progress) ->
                "${encodeUri(uri)}\t${progress.positionMs}\t${progress.durationMs}"
            },
        )
        persistedEntries = values
        mutableEntries.value = values
    }

    private fun load(): Map<String, PlaybackProgress> {
        val raw = runCatching { settings.getString(KEY_HISTORY, "") }.getOrDefault("")
        val restored = linkedMapOf<String, PlaybackProgress>()
        raw.lineSequence().forEach { line ->
            decodeEntry(line)?.let { (uri, progress) ->
                restored.remove(uri)
                restored[uri] = progress
                if (restored.size > MAX_ENTRIES) restored.remove(restored.keys.first())
            }
        }
        return restored
    }

    private companion object {
        const val KEY_HISTORY = "playback_history_v1"
        const val KEY_AUTO_PLAY_NEXT = "playback_auto_play_next"
        const val MAX_ENTRIES = 200

        // Fixed-width UTF-16 hex keeps arbitrary URIs separate from record delimiters.
        fun encodeUri(uri: String): String =
            uri.map { it.code.toString(16).padStart(4, '0') }.joinToString("")

        fun decodeEntry(line: String): Pair<String, PlaybackProgress>? {
            val fields = line.split('\t')
            if (!hasValidEncodedUri(fields)) return null
            val uri = buildString {
                for (chunk in fields[0].chunked(4)) {
                    append((chunk.toIntOrNull(16) ?: return null).toChar())
                }
            }
            val position = fields[1].toLongOrNull() ?: return null
            val duration = fields[2].toLongOrNull() ?: return null
            if (!hasValidProgress(uri, position, duration)) return null
            return uri to PlaybackProgress(position, duration)
        }

        private fun hasValidEncodedUri(fields: List<String>): Boolean =
            fields.size == 3 && fields[0].isNotEmpty() && fields[0].length % 4 == 0

        private fun hasValidProgress(uri: String, position: Long, duration: Long): Boolean =
            uri.isNotBlank() && position >= 0 && duration > 0 && position <= duration
    }
}
