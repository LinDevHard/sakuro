package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Combines detection layers: all run in parallel, and only a result no worse
 * than the one already shown is emitted. The fast layer (file name) gives an
 * instant answer; the slow layer (frames) later replaces it with a more confident one;
 * a less confident contradiction is discarded.
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
