package com.rinwave.sakuro.engine.media3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StreamCacheKeyTest {

    @Test
    fun `re-signed urls share one key`() {
        val first = "https://cdn.example/v/seg_12.ts?token=abc&expires=1700000000&quality=720"
        val second = "https://cdn.example/v/seg_12.ts?quality=720&expires=1700009999&token=xyz"
        assertEquals("https://cdn.example/v/seg_12.ts?quality=720", StreamCacheKey.of(first))
        assertEquals(StreamCacheKey.of(first), StreamCacheKey.of(second))
    }

    @Test
    fun `cloudfront, akamai, s3 and azure signatures are dropped`() {
        assertEquals(
            "https://d1.cloudfront.net/a.mp4",
            StreamCacheKey.of("https://d1.cloudfront.net/a.mp4?Expires=1&Signature=s&Key-Pair-Id=k"),
        )
        assertEquals("https://ak.example/a.m4s", StreamCacheKey.of("https://ak.example/a.m4s?hdnts=st=1~exp=2~hmac=3"))
        assertEquals(
            "https://b.s3.amazonaws.com/a.mp4",
            StreamCacheKey.of("https://b.s3.amazonaws.com/a.mp4?X-Amz-Signature=s&X-Amz-Date=d&X-Amz-Expires=60"),
        )
        assertEquals(
            "https://acc.blob.core.windows.net/c/a.mp4",
            StreamCacheKey.of("https://acc.blob.core.windows.net/c/a.mp4?sv=2022&se=2030&sr=b&sp=r&sig=x"),
        )
    }

    @Test
    fun `content-selecting parameters and paths stay distinct`() {
        assertNotEquals(
            StreamCacheKey.of("https://cdn.example/get?id=1&token=a"),
            StreamCacheKey.of("https://cdn.example/get?id=2&token=a"),
        )
        assertEquals("https://cdn.example/a.mp4", StreamCacheKey.of("https://cdn.example/a.mp4#t=10"))
        assertEquals("https://cdn.example/a.mp4", StreamCacheKey.of("https://cdn.example/a.mp4"))
    }
}
