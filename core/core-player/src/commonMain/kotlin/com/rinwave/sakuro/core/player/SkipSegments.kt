package com.rinwave.sakuro.core.player

enum class SkipKind { OPENING, ENDING }

/** A chapter the viewer usually skips, and where skipping lands. */
data class SkipSegment(
    val kind: SkipKind,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Recognizes opening/ending chapters by name, as fansub and BD releases title them:
 * "Opening", "OP", "OP2", "Intro", "オープニング", "Опенинг"; "Ending", "ED", "Credits",
 * "エンディング", "Эндинг", "Титры". Whole words only, so "Operation" or "Edge" stay chapters.
 */
object SkipSegments {
    /** The anime opening is 1:30 by convention; skipping 85 s lands just past it. */
    const val OPENING_JUMP_MS = 85_000L

    /** The button goes away this close to the segment's end; the jump would be pointless. */
    private const val MIN_REMAINING_MS = 1_500L

    private val opening = Regex(
        """(^|[^\p{L}\p{N}])(op|op\s?\d+|opening|intro|オープニング|опенинг|заставка|вступление|片头)([^\p{L}]|$)""",
    )
    private val ending = Regex(
        """(^|[^\p{L}\p{N}])(ed|ed\s?\d+|ending|credits|outro|エンディング|эндинг|титры|片尾)([^\p{L}]|$)""",
    )

    /** Lowercased first: a regex (?i) flag would fold only ASCII on the JVM, missing "Опенинг". */
    fun kindOf(title: String): SkipKind? {
        val name = title.lowercase()
        return when {
            opening.containsMatchIn(name) -> SkipKind.OPENING
            ending.containsMatchIn(name) -> SkipKind.ENDING
            else -> null
        }
    }

    fun all(chapters: List<Chapter>, durationMs: Long): List<SkipSegment> =
        chapters.mapIndexedNotNull { index, chapter ->
            val kind = kindOf(chapter.title) ?: return@mapIndexedNotNull null
            val end = Chapters.endOf(chapters, index, durationMs)
            if (end > chapter.startMs) SkipSegment(kind, chapter.startMs, end) else null
        }

    /** The segment playing at [positionMs] with something left to skip, if any. */
    fun at(chapters: List<Chapter>, positionMs: Long, durationMs: Long): SkipSegment? =
        all(chapters, durationMs).firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - MIN_REMAINING_MS }
}
