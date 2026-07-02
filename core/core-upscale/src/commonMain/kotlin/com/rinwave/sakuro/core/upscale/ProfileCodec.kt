package com.rinwave.sakuro.core.upscale

import kotlinx.serialization.json.Json

/** Импорт/экспорт пресетов файлом или строкой (FEATURES.md §2.2). */
object ProfileCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(profile: UpscaleProfile): String = json.encodeToString(UpscaleProfile.serializer(), profile)

    fun decode(raw: String): Result<UpscaleProfile> = runCatching {
        json.decodeFromString(UpscaleProfile.serializer(), raw).copy(builtIn = false)
    }
}
