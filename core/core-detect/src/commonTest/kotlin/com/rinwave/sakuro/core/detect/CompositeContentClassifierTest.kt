package com.rinwave.sakuro.core.detect

import com.rinwave.sakuro.core.upscale.ContentClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeContentClassifierTest {

    private val request = ClassificationRequest(uri = "file:///video.mp4", title = "video")

    @Test
    fun `медленный слой замещает быстрый при большей уверенности`() = runTest {
        val composite = CompositeContentClassifier(
            fixed(ContentDetection(ContentClass.ANIME, 0.75f, "filename")),
            delayed(ContentDetection(ContentClass.LIVE_ACTION, 0.85f, "frames")),
        )

        val results = composite.classify(request).toList()

        assertEquals(listOf("filename", "frames"), results.map { it.source })
        assertEquals(ContentClass.LIVE_ACTION, results.last().contentClass)
    }

    @Test
    fun `менее уверенное противоречие отбрасывается`() = runTest {
        val composite = CompositeContentClassifier(
            fixed(ContentDetection(ContentClass.ANIME, 0.9f, "filename")),
            delayed(ContentDetection(ContentClass.LIVE_ACTION, 0.7f, "frames")),
        )

        val results = composite.classify(request).toList()

        assertEquals(listOf("filename"), results.map { it.source })
    }

    @Test
    fun `UNKNOWN не эмитится наружу`() = runTest {
        val composite = CompositeContentClassifier(
            fixed(ContentDetection.UNKNOWN),
            delayed(ContentDetection(ContentClass.ANIME, 0.8f, "frames")),
        )

        val results = composite.classify(request).toList()

        assertEquals(listOf("frames"), results.map { it.source })
    }

    @Test
    fun `молчащие слои - пустой поток`() = runTest {
        val composite = CompositeContentClassifier(silent(), silent())

        assertEquals(emptyList(), composite.classify(request).toList())
    }

    private fun fixed(detection: ContentDetection): ContentClassifier =
        object : ContentClassifier {
            override fun classify(request: ClassificationRequest): Flow<ContentDetection> =
                flowOf(detection)
        }

    private fun delayed(detection: ContentDetection, delayMs: Long = 50): ContentClassifier =
        object : ContentClassifier {
            override fun classify(request: ClassificationRequest): Flow<ContentDetection> =
                flow {
                    delay(delayMs)
                    emit(detection)
                }
        }

    private fun silent(): ContentClassifier =
        object : ContentClassifier {
            override fun classify(request: ClassificationRequest): Flow<ContentDetection> =
                flow {}
        }
}
