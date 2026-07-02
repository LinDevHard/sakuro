package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilenameContentClassifierTest {

    private val classifier = FilenameContentClassifier()

    @Test
    fun knownFansubGroupIsAnimeWithHighConfidence() {
        val result = classifier.detect("[SubsPlease] Sousou no Frieren - 28 (1080p) [F02B9CEE].mkv")
        assertEquals(ContentClass.ANIME, result.contentClass)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun animeKeywordDetected() {
        val result = classifier.detect("Best.Anime.Movie.2023.1080p.WEB-DL.mkv")
        assertEquals(ContentClass.ANIME, result.contentClass)
    }

    @Test
    fun fansubNamingPatternDetectedAsAnime() {
        val result = classifier.detect("[Chihiro] Mahou Shoujo - 12 [1080p Hi10P AAC][ABCD1234].mkv")
        assertEquals(ContentClass.ANIME, result.contentClass)
    }

    @Test
    fun cartoonKeywordDetected() {
        val result = classifier.detect("Классный мультфильм (2020) 1080p.mp4")
        assertEquals(ContentClass.CARTOON, result.contentClass)
    }

    @Test
    fun genericMovieNameStaysUnknown() {
        val result = classifier.detect("Interstellar.2014.2160p.BluRay.x265.mkv")
        assertEquals(ContentClass.UNKNOWN, result.contentClass)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun caseInsensitiveGroupMatch() {
        val result = classifier.detect("[subsplease] one piece - 1100 (720p).mkv")
        assertEquals(ContentClass.ANIME, result.contentClass)
    }
}
