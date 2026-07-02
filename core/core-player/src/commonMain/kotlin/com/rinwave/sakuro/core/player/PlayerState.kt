package com.rinwave.sakuro.core.player

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val speed: Float = 1f,
    val tracks: List<TrackInfo> = emptyList(),
    val activeUpscaleProfileId: String? = null,
    val errorMessage: String? = null,
)

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
}

enum class TrackType {
    VIDEO,
    AUDIO,
    SUBTITLE,
}

data class TrackInfo(
    val id: String,
    val type: TrackType,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
)

data class TrackSelection(
    val trackId: String,
    val type: TrackType,
)
