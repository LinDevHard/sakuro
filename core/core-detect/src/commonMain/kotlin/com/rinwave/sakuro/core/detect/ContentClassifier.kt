package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow

/**
 * Авто-детект класса контента (FEATURES.md §1): определяет стиль изображения
 * для авто-выбора пресета. Не зависит от движка воспроизведения — работает
 * над метаданными и (в будущих реализациях) сэмплами кадров.
 */
interface ContentClassifier {

    /**
     * Поток уточняющихся результатов: быстрые эвристики (имя файла) эмитятся
     * сразу, тяжёлые (анализ кадров) — по мере готовности. Каждый следующий
     * результат замещает предыдущий.
     */
    fun classify(request: ClassificationRequest): Flow<ContentDetection>
}

/** Что известно о медиа до начала анализа. */
data class ClassificationRequest(
    val uri: String,
    val title: String,
)

/**
 * Результат детекции: класс + уверенность (0..1) — оба показываются
 * в debug-оверлее (FEATURES.md §4.1).
 */
data class ContentDetection(
    val contentClass: ContentClass,
    val confidence: Float,
    /** Какой анализатор дал результат: "filename", "frames"… */
    val source: String,
) {
    companion object {
        val UNKNOWN = ContentDetection(ContentClass.UNKNOWN, 0f, source = "none")
    }
}
