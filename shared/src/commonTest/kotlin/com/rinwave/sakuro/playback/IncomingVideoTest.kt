package com.rinwave.sakuro.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingVideoTest {
    @Test
    fun viewUsesLocalData() {
        assertEquals("content://media/video/12", incomingVideoUri(VIEW, "video/mp4", "content://media/video/12", null))
        assertEquals("file:///storage/movie.mp4", incomingVideoUri(VIEW, null, "file:///storage/movie.mp4", null))
    }

    @Test
    fun sendUsesStreamRatherThanData() {
        assertEquals(
            "content://provider/movie",
            incomingVideoUri(SEND, "video/mkv", "file:///wrong.mp4", "content://provider/movie"),
        )
        assertNull(incomingVideoUri(SEND, "video/mp4", "file:///wrong.mp4", null))
    }

    @Test
    fun rejectsNetworkMalformedAndNonVideoInputs() {
        listOf(
            "https://example.com/movie.mp4",
            "rtsp://host/movie",
            "content:///missing",
            "file:relative",
            "file://remote/movie",
            "content://",
            "content://host/bad%ZZ",
            "content://host/a\n",
        ).forEach {
            assertNull(incomingVideoUri(VIEW, "video/mp4", it, null), it)
        }
        assertNull(incomingVideoUri(VIEW, "text/plain", "content://provider/movie", null))
        assertNull(incomingVideoUri(SEND, null, null, "content://provider/movie"))
        assertNull(
            incomingVideoUri("android.intent.action.SEND_MULTIPLE", "video/mp4", null, "content://provider/movie"),
        )
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
    }
}
