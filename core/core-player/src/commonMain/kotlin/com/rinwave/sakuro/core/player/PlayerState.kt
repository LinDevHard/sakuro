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
    /** Technical error code for diagnostics; [error] carries the user-facing classification. */
    val errorMessage: String? = null,
    val error: PlaybackError? = null,
    /** Automatic reconnect in progress after a recoverable network failure; 0 when not reconnecting. */
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 0,
    /** A reconnect is parked until the device is back online. */
    val waitingForNetwork: Boolean = false,
    /** A live stream: no fixed duration, seeking is limited to the live window. */
    val isLive: Boolean = false,
    /** Live only: how far playback is behind the live edge (beyond the stream's target latency). */
    val liveBehindMs: Long = 0L,
    /** False for live streams without a DVR window and for unseekable progressive sources. */
    val isSeekable: Boolean = true,
    /** Selectable video variants of an adaptive stream, best first; empty when there is no choice. */
    val videoQualities: List<VideoQuality> = emptyList(),
    /** The user's fixed choice, or null for automatic (ABR) selection. */
    val selectedQualityId: String? = null,
    /** The variant currently being decoded, so "Auto" can say what it picked. */
    val playingQualityId: String? = null,
)

data class VideoQuality(
    val id: String,
    val width: Int,
    val height: Int,
    /** Peak/average bitrate in bits per second, or 0 when the manifest does not say. */
    val bitrate: Int = 0,
    val frameRate: Float = 0f,
) {
    /** "4K", "1080p", "720p60": the vertical resolution snapped to the familiar ladder. */
    val label: String get() = VideoQualityLabels.label(width, height, frameRate)
}

object VideoQualityLabels {
    private val ladder = listOf(144, 240, 360, 480, 540, 576, 720, 1080, 1440, 2160, 4320)
    private const val SNAP_TOLERANCE = 0.12f
    private const val HIGH_FRAME_RATE = 49f
    private const val UHD = 2160
    private const val UHD_8K = 4320

    /**
     * Uses the larger of the real height and the 16:9-equivalent of the width, so a
     * 1920×800 cinemascope variant reads as 1080p and a portrait 1080×1920 as 1080p.
     */
    fun nominalHeight(width: Int, height: Int): Int {
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        val equivalent = maxOf(shortSide, longSide * 9 / 16)
        val snapped = ladder.minByOrNull { kotlin.math.abs(it - equivalent) } ?: equivalent
        return if (kotlin.math.abs(snapped - equivalent) <= snapped * SNAP_TOLERANCE) snapped else equivalent
    }

    fun label(width: Int, height: Int, frameRate: Float = 0f): String {
        val nominal = nominalHeight(width, height)
        val base = when {
            nominal >= UHD_8K -> "8K"
            nominal >= UHD -> "4K"
            else -> "${nominal}p"
        }
        return if (frameRate >= HIGH_FRAME_RATE && nominal < UHD) "$base${frameRate.roundToFps()}" else base
    }

    private fun Float.roundToFps(): Int = kotlin.math.round(this).toInt()

    /** "6.2 Mbps" / "850 kbps"; null when unknown. */
    fun bitrateText(bitrate: Int): String? = when {
        bitrate <= 0 -> null
        bitrate >= 1_000_000 -> "${(bitrate / 100_000) / 10.0} Mbps"
        else -> "${bitrate / 1000} kbps"
    }
}

/** Why playback stopped, coarse enough for UI copy and retry decisions. */
data class PlaybackError(
    val kind: PlaybackErrorKind,
    /** Engine error code name, e.g. `ERROR_CODE_IO_BAD_HTTP_STATUS`. */
    val code: String,
    val httpStatus: Int? = null,
) {
    /** Whether an explicit "Retry" may help (DRM and unsupported formats never recover). */
    val retryable: Boolean
        get() = kind == PlaybackErrorKind.NETWORK ||
            kind == PlaybackErrorKind.SERVER ||
            kind == PlaybackErrorKind.UNKNOWN
}

enum class PlaybackErrorKind {
    /** No connectivity, DNS failure, timeout, connection reset. */
    NETWORK,

    /** HTTP 5xx/408/429: the server is there but failing for now. */
    SERVER,

    /** HTTP 4xx (except 408/429): the link is wrong, expired or forbidden. */
    HTTP_CLIENT,

    /** Cleartext blocked by policy or a TLS/certificate problem. */
    INSECURE,

    /** Protected (Widevine/PlayReady/FairPlay) content that Sakuro does not play. */
    DRM,

    /** Container, manifest or codec the device cannot play. */
    UNSUPPORTED,

    /** Local file missing or unreadable. */
    SOURCE,
    UNKNOWN,
}

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
    val trackId: String?,
    val type: TrackType,
)

data class ExternalSubtitle(
    val uri: String,
    val title: String,
)
