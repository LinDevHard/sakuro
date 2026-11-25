package com.rinwave.sakuro.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.rinwave.sakuro.core.player.PlaybackError
import com.rinwave.sakuro.core.player.PlaybackErrorKind
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.math.min

/** Maps Media3 failures to coarse [PlaybackErrorKind]s; pure so it can be unit-tested on the JVM. */
internal object StreamErrorClassifier {

    fun classify(error: PlaybackException): PlaybackError {
        val causes = generateSequence(error.cause) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        val status = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        return PlaybackError(classify(error.errorCode, status, causes), error.errorCodeName, status)
    }

    @Suppress("CyclomaticComplexMethod")
    fun classify(errorCode: Int, httpStatus: Int?, causes: List<Throwable>): PlaybackErrorKind {
        // TLS problems surface as generic connection failures; the cause tells them apart.
        if (causes.any { it is SSLException }) return PlaybackErrorKind.INSECURE
        // The server stopped updating (or reset) a live playlist: a stalled origin, worth
        // reconnecting to. Matched by name to keep this classifier free of the HLS module.
        if (causes.any { it.javaClass.simpleName in livePlaylistFailures }) return PlaybackErrorKind.SERVER
        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            -> PlaybackErrorKind.NETWORK

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                if (httpStatus != null && isTransientHttpStatus(httpStatus)) {
                    PlaybackErrorKind.SERVER
                } else {
                    PlaybackErrorKind.HTTP_CLIENT
                }

            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackErrorKind.INSECURE

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            -> PlaybackErrorKind.SOURCE

            // An HTML login/captcha page instead of media usually lands here.
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> PlaybackErrorKind.UNSUPPORTED

            in DRM_CODES -> PlaybackErrorKind.DRM

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> when {
                causes.any { it.isNetworkFailure() } -> PlaybackErrorKind.NETWORK
                else -> PlaybackErrorKind.UNKNOWN
            }

            else -> PlaybackErrorKind.UNKNOWN
        }
    }

    /** Kinds the engine retries by itself (a dropped connection or a flaky CDN). */
    fun isAutoRetryable(kind: PlaybackErrorKind): Boolean =
        kind == PlaybackErrorKind.NETWORK || kind == PlaybackErrorKind.SERVER

    fun isTransientHttpStatus(status: Int): Boolean =
        status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS || status in HTTP_SERVER_ERRORS

    /** Player-level reconnect delays: 1, 2, 4, 8, 16, 30 s. */
    fun reconnectDelayMs(attempt: Int): Long =
        min(RECONNECT_BASE_MS shl (attempt - 1).coerceIn(0, MAX_SHIFT), RECONNECT_MAX_MS)

    /** Chunk/manifest-level retry delays inside one prepare: 0.5, 1, 2, 4, 4 s. */
    fun loadRetryDelayMs(errorCount: Int): Long =
        min(LOAD_RETRY_BASE_MS shl (errorCount - 1).coerceIn(0, MAX_SHIFT), LOAD_RETRY_MAX_MS)

    private fun Throwable.isNetworkFailure(): Boolean =
        this is UnknownHostException ||
            this is ConnectException ||
            this is NoRouteToHostException ||
            this is SocketException ||
            this is InterruptedIOException

    const val MAX_RECONNECT_ATTEMPTS = 6
    const val MAX_LOAD_RETRIES = 5

    private val livePlaylistFailures = setOf("PlaylistStuckException", "PlaylistResetException")

    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_SHIFT = 10
    private const val RECONNECT_BASE_MS = 1_000L
    private const val RECONNECT_MAX_MS = 30_000L
    private const val LOAD_RETRY_BASE_MS = 500L
    private const val LOAD_RETRY_MAX_MS = 4_000L
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private val HTTP_SERVER_ERRORS = 500..599
    private val DRM_CODES =
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
}

/**
 * Retries failed manifest/segment loads with exponential backoff, but gives up at once on
 * permanent HTTP answers (an expired token or a wrong link will not heal by retrying). HLS
 * variant/CDN fallbacks still come from [DefaultLoadErrorHandlingPolicy.getFallbackSelectionFor].
 */
@OptIn(UnstableApi::class)
internal class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(StreamErrorClassifier.MAX_LOAD_RETRIES) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException &&
            !StreamErrorClassifier.isTransientHttpStatus(exception.responseCode)
        ) {
            return C.TIME_UNSET
        }
        return StreamErrorClassifier.loadRetryDelayMs(loadErrorInfo.errorCount)
    }
}
