package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Объединяет слои детекции: все запускаются параллельно, наружу уходит
 * только результат не хуже уже показанного. Быстрый слой (имя файла) даёт
 * мгновенный ответ, медленный (кадры) позже замещает его более уверенным;
 * менее уверенное противоречие отбрасывается.
 */
class CompositeContentClassifier(
    private val layers: List<ContentClassifier>,
) : ContentClassifier {

    constructor(vararg layers: ContentClassifier) : this(layers.toList())

    override fun classify(request: ClassificationRequest): Flow<ContentDetection> = channelFlow {
        var best = ContentDetection.UNKNOWN
        val mutex = Mutex()
        layers.forEach { layer ->
            launch {
                layer.classify(request).collect { detection ->
                    if (detection.contentClass == ContentClass.UNKNOWN) return@collect
                    mutex.withLock {
                        if (detection.confidence >= best.confidence) {
                            best = detection
                            send(detection)
                        }
                    }
                }
            }
        }
    }
}
