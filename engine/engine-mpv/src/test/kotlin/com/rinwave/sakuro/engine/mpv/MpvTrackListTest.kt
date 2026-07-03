package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.player.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvTrackListTest {

    @Test
    fun `парсит видео, аудио и субтитры из track-list`() {
        val json = """
            [
              {"id":1,"type":"video","selected":true,"codec":"h264","demux-w":1920,"demux-h":1080},
              {"id":1,"type":"audio","selected":true,"codec":"aac","lang":"jpn","demux-channel-count":2},
              {"id":2,"type":"audio","codec":"ac3","lang":"rus","title":"Дубляж","demux-channel-count":6},
              {"id":1,"type":"sub","codec":"ass","lang":"eng"}
            ]
        """.trimIndent()

        val tracks = parseMpvTrackList(json)

        assertEquals(4, tracks.size)
        assertEquals(TrackType.VIDEO, tracks[0].type)
        assertEquals("1920x1080 h264", tracks[0].label)
        assertTrue(tracks[0].selected)
        assertEquals("jpn (aac 2ch)", tracks[1].label)
        assertEquals("Дубляж", tracks[2].label)
        assertEquals("rus", tracks[2].language)
        assertEquals(TrackType.SUBTITLE, tracks[3].type)
        assertEquals("eng", tracks[3].label)
    }

    @Test
    fun `дорожки одного типа различаются числовым id mpv`() {
        val json = """[{"id":1,"type":"audio"},{"id":2,"type":"audio"}]"""

        val tracks = parseMpvTrackList(json)

        assertEquals(listOf("1", "2"), tracks.map { it.id })
    }

    @Test
    fun `неизвестные типы и мусор пропускаются`() {
        val json = """[{"id":1,"type":"attachment"},{"foo":"bar"},{"id":2,"type":"video"}]"""

        val tracks = parseMpvTrackList(json)

        assertEquals(1, tracks.size)
        assertEquals(TrackType.VIDEO, tracks[0].type)
    }

    @Test
    fun `не-JSON и пустая строка дают пустой список`() {
        assertEquals(emptyList(), parseMpvTrackList(""))
        assertEquals(emptyList(), parseMpvTrackList("not json"))
        assertEquals(emptyList(), parseMpvTrackList("{}"))
    }
}
