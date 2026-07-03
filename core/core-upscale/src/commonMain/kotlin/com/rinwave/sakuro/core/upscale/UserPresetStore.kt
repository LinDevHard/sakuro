package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Стор пользовательских пресетов (FEATURES.md §2.2): создание, редактирование,
 * удаление и импорт/экспорт строкой через [ProfileCodec]. Встроенные пресеты
 * ([BuiltInPresets]) не хранятся и не могут быть перезаписаны.
 *
 * Хранение — multiplatform-settings, весь список одним JSON-ключом:
 * пресетов единицы, атомарная перезапись списка проще миграций по ключам.
 */
class UserPresetStore(private val settings: Settings = Settings()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(UpscaleProfile.serializer())

    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<UpscaleProfile>> = _presets.asStateFlow()

    fun byId(id: String?): UpscaleProfile? = _presets.value.firstOrNull { it.id == id }

    /**
     * Сохраняет пресет: по своему id обновляет существующий, иначе создаёт
     * новый (пустой или занятый встроенным id заменяется на сгенерированный).
     * Возвращает фактически сохранённый профиль.
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

    /** Импорт строки: всегда сохраняется как НОВЫЙ пользовательский пресет. */
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

    /** id свободен, если не занят встроенным пресетом и «auto»-псевдопресетом. */
    private fun ensureUserId(id: String): String {
        if (id.isNotBlank() && !isReserved(id)) return id
        var candidate: String
        do {
            candidate = "user-" + Random.nextInt(RANDOM_ID_BOUND).toString(RADIX_HEX).padStart(ID_SUFFIX_LENGTH, '0')
        } while (isReserved(candidate) || byId(candidate) != null)
        return candidate
    }

    private fun isReserved(id: String): Boolean = id == AUTO_ID || BuiltInPresets.byId(id) != null

    /** Границы значений держим валидными независимо от источника (редактор/импорт). */
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
    )

    companion object {
        const val UPSCALE_MIN = 1f
        const val UPSCALE_MAX = 4f

        private const val KEY_PRESETS = "user_presets"
        private const val DEFAULT_NAME = "Мой пресет"
        private const val AUTO_ID = "auto"
        private const val RANDOM_ID_BOUND = 0x1000000
        private const val RADIX_HEX = 16
        private const val ID_SUFFIX_LENGTH = 6
    }
}
