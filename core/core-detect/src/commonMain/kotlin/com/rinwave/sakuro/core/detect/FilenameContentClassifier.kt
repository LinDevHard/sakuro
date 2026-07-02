package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Быстрая эвристика по имени файла/релиза — первый, мгновенный слой детекции.
 * Опирается на конвенции релизов: тег фансаб-группы в квадратных скобках,
 * ключевые слова, паттерн «[Группа] Тайтл - 07». Анализ кадров (второй слой)
 * при появлении заместит этот результат более уверенным.
 */
class FilenameContentClassifier : ContentClassifier {

    override fun classify(request: ClassificationRequest): Flow<ContentDetection> =
        flowOf(detect(request.title.ifBlank { request.uri.substringAfterLast('/') }))

    fun detect(name: String): ContentDetection {
        val normalized = name.lowercase()

        knownAnimeGroups.firstOrNull { normalized.contains("[$it]") }?.let {
            return ContentDetection(ContentClass.ANIME, confidence = 0.9f, source = SOURCE)
        }
        if (animeKeywords.any { normalized.contains(it) }) {
            return ContentDetection(ContentClass.ANIME, confidence = 0.75f, source = SOURCE)
        }
        if (cartoonKeywords.any { normalized.contains(it) }) {
            return ContentDetection(ContentClass.CARTOON, confidence = 0.6f, source = SOURCE)
        }
        // «[Группа] Тайтл - 07 …» — типичная схема именования аниме-релизов.
        if (fansubPattern.containsMatchIn(name)) {
            return ContentDetection(ContentClass.ANIME, confidence = 0.6f, source = SOURCE)
        }
        return ContentDetection.UNKNOWN
    }

    private companion object {
        const val SOURCE = "filename"

        val knownAnimeGroups = listOf(
            "subsplease", "erai-raws", "horriblesubs", "judas", "ember",
            "asw", "yameii", "anime time", "seanime",
        )

        val animeKeywords = listOf("anime", "аниме", "ova]", " ova ", "bdrip 10bit")

        val cartoonKeywords = listOf("cartoon", "мультфильм", "мультсериал")

        val fansubPattern = Regex("""^\[[^\]]+\]\s?.+\s-\s?\d{1,4}\b""")
    }
}
