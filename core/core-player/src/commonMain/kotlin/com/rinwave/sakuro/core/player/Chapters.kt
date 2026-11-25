package com.rinwave.sakuro.core.player

/** A container chapter (MKV/MP4 chapter list, mpv `chapter-list`). An empty title is untitled. */
data class Chapter(
    val startMs: Long,
    val title: String = "",
)

object Chapters {
    /** Sorted by start, one chapter per start time, nothing before 0 or past [durationMs] (when known). */
    fun normalize(chapters: List<Chapter>, durationMs: Long = 0L): List<Chapter> =
        chapters
            .map { it.copy(startMs = it.startMs.coerceAtLeast(0L), title = it.title.trim()) }
            .filter { durationMs <= 0L || it.startMs < durationMs }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
            // A single chapter covering the whole file carries no navigation.
            .takeIf { it.size > 1 }
            .orEmpty()

    /** Index of the chapter playing at [positionMs], or -1 before the first chapter. */
    fun indexAt(chapters: List<Chapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.startMs <= positionMs }

    /** Where chapter [index] ends: the next chapter's start, else the end of the media. */
    fun endOf(chapters: List<Chapter>, index: Int, durationMs: Long): Long =
        chapters.getOrNull(index + 1)?.startMs ?: durationMs
}
