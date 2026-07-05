package com.rinwave.sakuro.core.media

/** Desktop target — a UI sandbox (ARCHITECTURE.md §3.2): the library is filled with samples. */
class SampleVideoLibrary : MediaLibrary {

    override suspend fun scan(): List<VideoItem> {
        val now = nowEpochSeconds()
        return recentSamples(now) + archiveSamples(now)
    }

    private fun recentSamples(now: Long): List<VideoItem> = listOf(
        VideoItem(
            id = "sample-1",
            title = "A Walk Under the Sakura [480p].mkv",
            uri = "fake://sample-1",
            durationMs = 24 * 60_000L + 12_000L,
            width = 854,
            height = 480,
            sizeBytes = 260L * 1024 * 1024,
            dateAddedEpochSec = now - 2 * 60,
            folderName = "Anime",
        ),
        VideoItem(
            id = "sample-2",
            title = "Fuji Twilight — Trailer [1080p].mp4",
            uri = "fake://sample-2",
            durationMs = 2 * 60_000L + 31_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 190L * 1024 * 1024,
            dateAddedEpochSec = now - DAY,
            folderName = "Movies",
        ),
        VideoItem(
            id = "sample-3",
            title = "Night City, Episode 07 [720p].mkv",
            uri = "fake://sample-3",
            durationMs = 23 * 60_000L + 40_000L,
            width = 1280,
            height = 720,
            sizeBytes = 610L * 1024 * 1024,
            dateAddedEpochSec = now - 4 * DAY,
            folderName = "Anime",
        ),
    )

    private fun archiveSamples(now: Long): List<VideoItem> = listOf(
        VideoItem(
            id = "sample-4",
            title = "Volcano Documentary [2160p].mp4",
            uri = "fake://sample-4",
            durationMs = 51 * 60_000L,
            width = 3840,
            height = 2160,
            sizeBytes = 3_400L * 1024 * 1024,
            dateAddedEpochSec = now - 12 * DAY,
            folderName = "Movies",
        ),
        VideoItem(
            id = "sample-5",
            title = "IMG_2043.mov",
            uri = "fake://sample-5",
            durationMs = 47_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 88L * 1024 * 1024,
            dateAddedEpochSec = now - 40 * DAY,
            folderName = "Camera",
        ),
        VideoItem(
            id = "sample-6",
            title = "Sunset Timelapse [2160p].mp4",
            uri = "fake://sample-6",
            durationMs = 3 * 60_000L + 8_000L,
            width = 3840,
            height = 2160,
            sizeBytes = 1_150L * 1024 * 1024,
            dateAddedEpochSec = now - 55 * DAY,
            folderName = "Camera",
        ),
    )

    private companion object {
        const val DAY = 86_400L
    }
}
