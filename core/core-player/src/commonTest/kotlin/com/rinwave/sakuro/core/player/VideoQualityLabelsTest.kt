package com.rinwave.sakuro.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoQualityLabelsTest {

    @Test
    fun `standard ladders read as familiar names`() {
        assertEquals("4K", VideoQualityLabels.label(3840, 2160))
        assertEquals("1440p", VideoQualityLabels.label(2560, 1440))
        assertEquals("1080p", VideoQualityLabels.label(1920, 1080))
        assertEquals("720p", VideoQualityLabels.label(1280, 720))
        assertEquals("480p", VideoQualityLabels.label(854, 480))
        assertEquals("360p", VideoQualityLabels.label(640, 360))
        assertEquals("8K", VideoQualityLabels.label(7680, 4320))
    }

    @Test
    fun `cropped, padded and portrait variants snap to the ladder`() {
        assertEquals("1080p", VideoQualityLabels.label(1920, 800))
        assertEquals("1080p", VideoQualityLabels.label(1920, 1088))
        assertEquals("720p", VideoQualityLabels.label(1280, 534))
        assertEquals("1080p", VideoQualityLabels.label(1080, 1920))
        assertEquals("4K", VideoQualityLabels.label(4096, 1716))
    }

    @Test
    fun `high frame rate is appended below 4K`() {
        assertEquals("1080p60", VideoQualityLabels.label(1920, 1080, 59.94f))
        assertEquals("720p", VideoQualityLabels.label(1280, 720, 30f))
        assertEquals("4K", VideoQualityLabels.label(3840, 2160, 60f))
    }

    @Test
    fun `bitrate text is compact`() {
        assertEquals("6.2 Mbps", VideoQualityLabels.bitrateText(6_250_000))
        assertEquals("850 kbps", VideoQualityLabels.bitrateText(850_000))
        assertNull(VideoQualityLabels.bitrateText(0))
    }
}
