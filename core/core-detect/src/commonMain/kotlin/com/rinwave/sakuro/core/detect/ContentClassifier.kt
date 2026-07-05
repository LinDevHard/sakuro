package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.flow.Flow

/**
 * Automatic content-class detection (FEATURES.md §1): determines the image style
 * for automatic preset selection. Independent of the playback engine — it works
 * over metadata and (in future implementations) frame samples.
 */
interface ContentClassifier {

    /**
     * A stream of refining results: fast heuristics (file name) are emitted
     * immediately, heavy ones (frame analysis) as they become ready. Each next
     * result replaces the previous one.
     */
    fun classify(request: ClassificationRequest): Flow<ContentDetection>
}

/** What is known about the media before analysis begins. */
data class ClassificationRequest(
    val uri: String,
    val title: String,
)

/**
 * Detection result: class + confidence (0..1) — both are shown
 * in the debug overlay (FEATURES.md §4.1).
 */
data class ContentDetection(
    val contentClass: ContentClass,
    val confidence: Float,
    /** Which analyzer produced the result: "filename", "frames"… */
    val source: String,
) {
    companion object {
        val UNKNOWN = ContentDetection(ContentClass.UNKNOWN, 0f, source = "none")
    }
}
