package com.rinwave.sakuro.engine.media3

import androidx.media3.common.PlaybackException
import com.rinwave.sakuro.core.player.PlaybackErrorKind
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamErrorClassifierTest {

    private fun kind(code: Int, status: Int? = null, vararg causes: Throwable) =
        StreamErrorClassifier.classify(code, status, causes.toList())

    @Test
    fun `connection failures and timeouts are network errors`() {
        assertEquals(PlaybackErrorKind.NETWORK, kind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(PlaybackErrorKind.NETWORK, kind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(
            PlaybackErrorKind.NETWORK,
            kind(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, IOException("wrapped"), UnknownHostException("cdn")),
        )
        assertEquals(
            PlaybackErrorKind.NETWORK,
            kind(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, SocketTimeoutException()),
        )
        assertEquals(PlaybackErrorKind.UNKNOWN, kind(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun `http status splits transient server errors from permanent client errors`() {
        val bad = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        assertEquals(PlaybackErrorKind.SERVER, kind(bad, 503))
        assertEquals(PlaybackErrorKind.SERVER, kind(bad, 429))
        assertEquals(PlaybackErrorKind.SERVER, kind(bad, 408))
        assertEquals(PlaybackErrorKind.HTTP_CLIENT, kind(bad, 403))
        assertEquals(PlaybackErrorKind.HTTP_CLIENT, kind(bad, 404))
        assertEquals(PlaybackErrorKind.HTTP_CLIENT, kind(bad, null))
    }

    @Test
    fun `tls failures and blocked cleartext are insecure`() {
        assertEquals(
            PlaybackErrorKind.INSECURE,
            kind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, SSLHandshakeException("cert")),
        )
        assertEquals(PlaybackErrorKind.INSECURE, kind(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED))
    }

    @Test
    fun `drm and format failures never auto retry`() {
        val drm = kind(PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED)
        val format = kind(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)
        val html = kind(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE)
        assertEquals(PlaybackErrorKind.DRM, drm)
        assertEquals(PlaybackErrorKind.UNSUPPORTED, format)
        assertEquals(PlaybackErrorKind.UNSUPPORTED, html)
        assertFalse(StreamErrorClassifier.isAutoRetryable(drm))
        assertFalse(StreamErrorClassifier.isAutoRetryable(format))
        assertTrue(StreamErrorClassifier.isAutoRetryable(PlaybackErrorKind.NETWORK))
        assertTrue(StreamErrorClassifier.isAutoRetryable(PlaybackErrorKind.SERVER))
    }

    /** Same simple name as Media3's HLS exception; the classifier matches by name. */
    private class PlaylistStuckException : IOException("stuck")

    @Test
    fun `a stuck live playlist is a server-side stall worth reconnecting to`() {
        val kind = kind(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, PlaylistStuckException())
        assertEquals(PlaybackErrorKind.SERVER, kind)
        assertTrue(StreamErrorClassifier.isAutoRetryable(kind))
    }

    @Test
    fun `backoff grows exponentially and is capped`() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (1..7).map(StreamErrorClassifier::reconnectDelayMs),
        )
        assertEquals(
            listOf(500L, 1_000L, 2_000L, 4_000L, 4_000L),
            (1..5).map(StreamErrorClassifier::loadRetryDelayMs),
        )
    }
}
