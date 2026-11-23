package com.rinwave.sakuro.core.model

import com.rinwave.sakuro.core.model.StreamUrl.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamUrlTest {

    private fun valid(input: String): StreamUrl = assertIs<ParseResult.Valid>(StreamUrl.parse(input)).url

    @Test
    fun `accepts http and https links and trims whitespace`() {
        assertEquals("https://example.com/a.mp4", valid("  https://example.com/a.mp4 \n").value)
        assertEquals("http://10.0.0.2:8080/live.m3u8", valid("http://10.0.0.2:8080/live.m3u8").value)
        assertEquals("https://Example.com/X.mp4", valid("HTTPS://Example.com/X.mp4").value)
    }

    @Test
    fun `adds https to a bare host link`() {
        assertEquals("https://example.com/video.mp4", valid("example.com/video.mp4").value)
    }

    @Test
    fun `rejects empty, foreign schemes and malformed links`() {
        assertEquals(ParseResult.Empty, StreamUrl.parse("   "))
        assertEquals(ParseResult.UnsupportedScheme, StreamUrl.parse("rtmp://example.com/live"))
        assertEquals(ParseResult.UnsupportedScheme, StreamUrl.parse("file:///sdcard/a.mp4"))
        assertEquals(ParseResult.Malformed, StreamUrl.parse("https://"))
        assertEquals(ParseResult.Malformed, StreamUrl.parse("https://exa mple.com/a.mp4"))
        assertEquals(ParseResult.Malformed, StreamUrl.parse("https://example.com:99999/a.mp4"))
        assertEquals(ParseResult.Malformed, StreamUrl.parse("not a link"))
    }

    @Test
    fun `detects the stream format from the path and query hints`() {
        assertEquals(StreamFormat.HLS, valid("https://cdn.example/x/master.m3u8?token=1").format)
        assertEquals(StreamFormat.HLS, valid("https://cdn.example/play?type=m3u8").format)
        assertEquals(StreamFormat.DASH, valid("https://cdn.example/x/manifest.mpd").format)
        assertEquals(StreamFormat.PROGRESSIVE, valid("https://cdn.example/x/movie.mp4?sig=m3u8").format)
        assertEquals(StreamFormat.PROGRESSIVE, valid("https://cdn.example/x/movie").format)
    }

    @Test
    fun `title uses a meaningful path segment or falls back to the host`() {
        assertEquals("Big Buck Bunny.mp4", valid("https://cdn.example/v/Big%20Buck%20Bunny.mp4?x=1").title)
        assertEquals("cdn.example", valid("https://cdn.example/hls/master.m3u8").title)
        assertEquals("cdn.example", valid("https://user@cdn.example:8443/").title)
    }

    @Test
    fun `remote check covers only http and https`() {
        assertTrue(StreamUrl.isRemote("https://a.b/c"))
        assertTrue(StreamUrl.isRemote("HTTP://a.b/c"))
        assertFalse(StreamUrl.isRemote("content://media/external/video/1"))
        assertFalse(StreamUrl.isRemote("file:///sdcard/a.mp4"))
    }
}
