package com.rinwave.sakuro.core.media

/** Library sort field (FEATURES: library sorting). Display labels live in the UI layer. */
enum class LibrarySort {
    DateAdded,
    Name,
    Size,
    Duration,
    Resolution,
    ;

    /** Default direction for this field: descending for everything, ascending for name. */
    val defaultDescending: Boolean get() = this != Name
}

/** Sort order: field + direction. */
data class SortOrder(
    val field: LibrarySort = LibrarySort.DateAdded,
    val descending: Boolean = true,
)

/** Relative age bucket used as a section header for date-sorted lists (resolved to text in the UI). */
enum class DateBucket { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, EARLIER }

/** A section of the video list; [bucket] is null for a single, unheadered section. */
data class VideoSection(val bucket: DateBucket?, val items: List<VideoItem>)

/** A folder of videos with a cover and count; [isOther] marks the "no folder" catch-all. */
data class VideoFolder(val name: String, val items: List<VideoItem>) {
    val count: Int get() = items.size
    val cover: VideoItem get() = items.first()
    val isOther: Boolean get() = name == FOLDER_OTHER
}

private const val DAY_SECONDS = 86_400L

private fun VideoItem.folderKey(): String = folderName.ifBlank { FOLDER_OTHER }

private fun comparatorFor(field: LibrarySort): Comparator<VideoItem> = when (field) {
    LibrarySort.DateAdded -> compareBy { it.dateAddedEpochSec }
    LibrarySort.Name -> compareBy { it.title.lowercase() }
    LibrarySort.Size -> compareBy { it.sizeBytes }
    LibrarySort.Duration -> compareBy { it.durationMs }
    LibrarySort.Resolution -> compareBy { it.width.toLong() * it.height }
}

/** Sort by [order]; ties are broken by title so the order stays stable. */
fun List<VideoItem>.sortedBy(order: SortOrder): List<VideoItem> {
    val base = comparatorFor(order.field).thenBy { it.title.lowercase() }
    val cmp = if (order.descending) base.reversed() else base
    return sortedWith(cmp)
}

/** Group by folder; named folders come first (alphabetically), the "Other" catch-all last. */
fun List<VideoItem>.toFolders(order: SortOrder): List<VideoFolder> =
    groupBy { it.folderKey() }
        .map { (name, items) -> VideoFolder(name, items.sortedBy(order)) }
        .sortedWith(compareBy({ it.isOther }, { it.name.lowercase() }))

/**
 * Group into sections. When sorting by date added, items are bucketed by age
 * (Today / Yesterday / This week / This month / Earlier), Google Photos style;
 * for any other field it returns a single unheadered section.
 */
fun List<VideoItem>.toSections(
    order: SortOrder,
    nowEpochSec: Long = nowEpochSeconds(),
): List<VideoSection> {
    val sorted = sortedBy(order)
    if (order.field != LibrarySort.DateAdded || sorted.isEmpty()) {
        return if (sorted.isEmpty()) emptyList() else listOf(VideoSection(null, sorted))
    }
    val today = nowEpochSec / DAY_SECONDS
    val grouped = LinkedHashMap<DateBucket, MutableList<VideoItem>>()
    for (item in sorted) {
        val bucket = dateBucketFor(today - item.dateAddedEpochSec / DAY_SECONDS)
        grouped.getOrPut(bucket) { mutableListOf() }.add(item)
    }
    return grouped.map { (bucket, items) -> VideoSection(bucket, items) }
}

private fun dateBucketFor(dayDiff: Long): DateBucket = when {
    dayDiff <= 0 -> DateBucket.TODAY
    dayDiff == 1L -> DateBucket.YESTERDAY
    dayDiff in 2..6 -> DateBucket.THIS_WEEK
    dayDiff in 7..29 -> DateBucket.THIS_MONTH
    else -> DateBucket.EARLIER
}
