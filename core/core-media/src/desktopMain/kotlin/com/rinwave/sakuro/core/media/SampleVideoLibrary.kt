package com.rinwave.sakuro.core.media

/** Desktop-таргет — полигон для UI (ARCHITECTURE.md §3.2): библиотека наполняется сэмплами. */
class SampleVideoLibrary : MediaLibrary {

    override suspend fun scan(): List<VideoItem> = listOf(
        VideoItem(
            id = "sample-1",
            title = "Прогулка под сакурой [480p].mkv",
            uri = "fake://sample-1",
            durationMs = 24 * 60_000L + 12_000L,
            width = 854,
            height = 480,
            sizeBytes = 260L * 1024 * 1024,
        ),
        VideoItem(
            id = "sample-2",
            title = "Fuji Twilight — трейлер [1080p].mp4",
            uri = "fake://sample-2",
            durationMs = 2 * 60_000L + 31_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 190L * 1024 * 1024,
        ),
        VideoItem(
            id = "sample-3",
            title = "Ночной город, эпизод 07 [720p].mkv",
            uri = "fake://sample-3",
            durationMs = 23 * 60_000L + 40_000L,
            width = 1280,
            height = 720,
            sizeBytes = 610L * 1024 * 1024,
        ),
        VideoItem(
            id = "sample-4",
            title = "Документалка о вулканах [2160p].mp4",
            uri = "fake://sample-4",
            durationMs = 51 * 60_000L,
            width = 3840,
            height = 2160,
            sizeBytes = 3_400L * 1024 * 1024,
        ),
    )
}
