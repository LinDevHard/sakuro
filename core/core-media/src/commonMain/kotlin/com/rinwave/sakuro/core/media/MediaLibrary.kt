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
)

/** Сканер локальной библиотеки; реализации по платформам (MediaStore на Android). */
interface MediaLibrary {
    suspend fun scan(): List<VideoItem>
}

/** Сигналы «перечитать библиотеку» (например, после выдачи разрешения на чтение медиа). */
object LibraryEvents {
    val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }
}
