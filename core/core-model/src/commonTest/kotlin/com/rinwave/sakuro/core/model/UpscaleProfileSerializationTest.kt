package com.rinwave.sakuro.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * User presets are persisted as JSON. The format must not depend on the Kotlin
 * package of these types: a moved class without stable @SerialName values
 * would silently drop every saved preset.
 */
class UpscaleProfileSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesPersistedPresetPayload() {
        val stored = """
            {"id":"user-abc123","name":"Mine","contentClass":"ANIME",
             "passes":[{"type":"denoise","strength":0.3},{"type":"upscale","factor":2.0},{"type":"sharpen","strength":0.5}],
             "shaderChain":["Anime4K_Restore_CNN_S.glsl"],"shaderParams":{"Anime4K_Restore_CNN_S.glsl":{"STRENGTH":0.7}}}
        """.trimIndent()

        val profile = json.decodeFromString(UpscaleProfile.serializer(), stored)

        assertEquals(
            UpscaleProfile(
                id = "user-abc123",
                name = "Mine",
                contentClass = ContentClass.ANIME,
                passes = listOf(UpscalePass.Denoise(0.3f), UpscalePass.Upscale(2f), UpscalePass.Sharpen(0.5f)),
                shaderChain = listOf("Anime4K_Restore_CNN_S.glsl"),
                shaderParams = mapOf("Anime4K_Restore_CNN_S.glsl" to mapOf("STRENGTH" to 0.7f)),
            ),
            profile,
        )
    }

    @Test
    fun encodesPassesWithStableTypeNames() {
        val encoded = json.encodeToString(UpscalePass.serializer(), UpscalePass.Upscale(2f))

        assertEquals("""{"type":"upscale","factor":2.0}""", encoded)
    }
}
