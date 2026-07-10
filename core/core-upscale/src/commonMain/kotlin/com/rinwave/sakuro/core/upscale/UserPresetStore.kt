package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * User preset store (FEATURES.md §2.2): creation, editing,
 * deletion and string import/export via [ProfileCodec]. Built-in presets
 * ([BuiltInPresets]) are not stored and cannot be overwritten.
 *
 * Storage — multiplatform-settings, the whole list under one JSON key:
 * there are only a few presets; atomic list rewrite is simpler than per-key migrations.
 */
class UserPresetStore(private val settings: Settings = Settings()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(UpscaleProfile.serializer())

    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<UpscaleProfile>> = _presets.asStateFlow()

    fun byId(id: String?): UpscaleProfile? = _presets.value.firstOrNull { it.id == id }

    /**
     * Saves a preset: updates the existing one by its id, otherwise creates
     * a new one (an empty id, or one taken by a built-in, is replaced by a generated id).
     * Returns the actually saved profile.
     */
    fun save(profile: UpscaleProfile): UpscaleProfile {
        val sanitized = profile.sanitized()
        val stored = if (byId(sanitized.id) != null) sanitized else sanitized.copy(id = ensureUserId(sanitized.id))
        persist(_presets.value.filterNot { it.id == stored.id } + stored)
        return stored
    }

    fun delete(id: String) {
        persist(_presets.value.filterNot { it.id == id })
    }

    fun export(profile: UpscaleProfile): String = ProfileCodec.encode(profile)

    /** String import: always saved as a NEW user preset. */
    fun import(raw: String): Result<UpscaleProfile> =
        ProfileCodec.decode(raw).map { save(it.copy(id = "")) }

    private fun persist(list: List<UpscaleProfile>) {
        val ordered = list.sortedBy { it.name.lowercase() }
        settings.putString(KEY_PRESETS, json.encodeToString(serializer, ordered))
        _presets.value = ordered
    }

    private fun load(): List<UpscaleProfile> {
        val raw = settings.getStringOrNull(KEY_PRESETS) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .map { it.sanitized() }
    }

    /** An id is free if not taken by a built-in preset or the "auto" pseudo-preset. */
    private fun ensureUserId(id: String): String {
        if (id.isNotBlank() && !isReserved(id)) return id
        var candidate: String
        do {
            candidate = "user-" + Random.nextInt(RANDOM_ID_BOUND).toString(RADIX_HEX).padStart(ID_SUFFIX_LENGTH, '0')
        } while (isReserved(candidate) || byId(candidate) != null)
        return candidate
    }

    private fun isReserved(id: String): Boolean = id == AUTO_ID || BuiltInPresets.byId(id) != null

    /** Keep value bounds valid regardless of source (editor/import). */
    private fun UpscaleProfile.sanitized(): UpscaleProfile = copy(
        name = name.trim().ifEmpty { DEFAULT_NAME },
        builtIn = false,
        passes = passes.map { pass ->
            when (pass) {
                is UpscalePass.Upscale -> UpscalePass.Upscale(pass.factor.coerceIn(UPSCALE_MIN, UPSCALE_MAX))
                is UpscalePass.Sharpen -> UpscalePass.Sharpen(pass.strength.coerceIn(0f, 1f))
                is UpscalePass.Denoise -> UpscalePass.Denoise(pass.strength.coerceIn(0f, 1f))
            }
        },
        // Chain entries are file names inside the shader store — never paths.
        shaderChain = shaderChain
            .map { UserShaderStore.sanitizeName(it) }
            .distinct()
            .take(UserShaderStore.MAX_CHAIN),
    )

    companion object {
        const val UPSCALE_MIN = 1f
        const val UPSCALE_MAX = 4f

        private const val KEY_PRESETS = "user_presets"
        private const val DEFAULT_NAME = "My preset"
        private const val AUTO_ID = "auto"
        private const val RANDOM_ID_BOUND = 0x1000000
        private const val RADIX_HEX = 16
        private const val ID_SUFFIX_LENGTH = 6
    }
}
