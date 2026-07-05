package com.rinwave.sakuro.core.media

/** Library sort field (FEATURES: library sorting). */
enum class LibrarySort(val label: String) {
    DateAdded("Date added"),
    Name("Name"),
    Size("Size"),
    Duration("Duration"),
    Resolution("Resolution"),
    ;

    /** Default direction for this field: descending for everything, ascending for name. */
    val defaultDescending: Boolean get() = this != Name
}

/** Sort order: field + direction. */
data class SortOrder(
    val field: LibrarySort = LibrarySort.DateAdded,
    val descending: Boolean = true,
)

/** A section of the video list (a title and its items). */
data class VideoSection(val title: String, val items: List<VideoItem>)

/** A folder of videos with a cover and count. */
data class VideoFolder(val name: String, val items: List<VideoItem>) {
    val count: Int get() = items.size
    val cover: VideoItem get() = items.first()
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

/** Group by folder; folders are sorted by name, items inside each by [order]. */
fun List<VideoItem>.toFolders(order: SortOrder): List<VideoFolder> =
    groupBy { it.folderKey() }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (name, items) -> VideoFolder(name, items.sortedBy(order)) }

/**
 * Group into sections. When sorting by date added, items are bucketed by age
 * (Today / Yesterday / This week / This month / Older), Google Photos style;
 * for any other field it returns a single unnamed section.
 */
fun List<VideoItem>.toSections(
    order: SortOrder,
    nowEpochSec: Long = nowEpochSeconds(),
): List<VideoSection> {
    val sorted = sortedBy(order)
    if (order.field != LibrarySort.DateAdded || sorted.isEmpty()) {
        return if (sorted.isEmpty()) emptyList() else listOf(VideoSection("", sorted))
    }
    val today = nowEpochSec / DAY_SECONDS
    val grouped = LinkedHashMap<String, MutableList<VideoItem>>()
    for (item in sorted) {
        val bucket = dateBucketLabel(today - item.dateAddedEpochSec / DAY_SECONDS)
        grouped.getOrPut(bucket) { mutableListOf() }.add(item)
    }
    return grouped.map { (title, items) -> VideoSection(title, items) }
}

private fun dateBucketLabel(dayDiff: Long): String = when {
    dayDiff <= 0 -> "Today"
    dayDiff == 1L -> "Yesterday"
    dayDiff in 2..6 -> "This week"
    dayDiff in 7..29 -> "This month"
    else -> "Earlier"
}
