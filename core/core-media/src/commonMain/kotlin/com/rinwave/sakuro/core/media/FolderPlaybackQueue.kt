package com.rinwave.sakuro.core.media

/** Natural title order within a known folder; navigation stops at either end. */
fun nextVideoInFolder(items: List<VideoItem>, currentUri: String): VideoItem? =
    adjacentVideo(items, currentUri, 1)

fun previousVideoInFolder(items: List<VideoItem>, currentUri: String): VideoItem? =
    adjacentVideo(items, currentUri, -1)

private fun adjacentVideo(items: List<VideoItem>, currentUri: String, offset: Int): VideoItem? {
    val current = items.firstOrNull { it.uri == currentUri } ?: return null
    if (current.folderId.isBlank() && current.folderName.isBlank()) return null
    val ordered = items.filter { it.sharesFolderWith(current) }.distinctBy { it.uri }.sortedWith(
        Comparator<VideoItem> { a, b -> compareNaturalTitles(a.title.lowercase(), b.title.lowercase()) }
            .thenBy { it.title }.thenBy { it.uri }.thenBy { it.id },
    )
    return ordered.getOrNull(ordered.indexOfFirst { it.uri == currentUri } + offset)
}

private fun VideoItem.sharesFolderWith(other: VideoItem): Boolean =
    if (folderId.isNotBlank() || other.folderId.isNotBlank()) {
        folderId.isNotBlank() && folderId == other.folderId
    } else {
        folderName == other.folderName
    }

/** Compare digit runs by significant length, avoiding overflow even for very long episode numbers. */
private fun compareNaturalTitles(left: String, right: String): Int {
    val leftParts = TITLE_PARTS.findAll(left).map { it.value }.toList()
    val rightParts = TITLE_PARTS.findAll(right).map { it.value }.toList()
    for (index in 0 until minOf(leftParts.size, rightParts.size)) {
        val a = leftParts[index]
        val b = rightParts[index]
        val result = if (a.first() in '0'..'9' && b.first() in '0'..'9') {
            compareDigitRuns(a, b)
        } else {
            a.compareTo(b)
        }
        if (result != 0) return result
    }
    return leftParts.size.compareTo(rightParts.size)
}

private fun compareDigitRuns(left: String, right: String): Int {
    val a = left.trimStart('0')
    val b = right.trimStart('0')
    return a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
}

private val TITLE_PARTS = Regex("[0-9]+|[^0-9]+")
