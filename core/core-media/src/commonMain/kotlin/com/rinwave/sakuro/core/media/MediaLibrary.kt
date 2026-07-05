package com.rinwave.sakuro.core.media

import kotlinx.coroutines.flow.MutableSharedFlow

data class VideoItem(
    val id: String,
    val title: String,
    val uri: String,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val dateAddedEpochSec: Long = 0,
    /** Folder bucket name (the bucket on Android); empty if unknown, then shown as [FOLDER_OTHER]. */
    val folderName: String = "",
)

/** Label for files without a bucket. */
const val FOLDER_OTHER: String = "Other"

/** Local-library scanner; per-platform implementations (MediaStore on Android). */
interface MediaLibrary {
    /** One-shot scan of the whole library (without watching for changes). */
    suspend fun scan(): List<VideoItem>

    /**
     * Ask the platform to re-index the media library. Useful when files
     * appeared outside the app (adb push, a file manager) and are not yet
     * in MediaStore. A successful rescan triggers [LibraryEvents.requestRefresh]
     * via the ContentObserver. Default is a no-op (desktop has no indexing).
     */
    fun requestSystemRescan() {}
}

/** "Re-read the library" signals (for example, after media-read permission is granted). */
object LibraryEvents {
    val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }
}
