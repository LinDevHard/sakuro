package com.rinwave.sakuro.core.upscale

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPresetStoreTest {

    private fun profile(id: String = "", name: String = "Test") = UpscaleProfile(
        id = id,
        name = name,
        passes = listOf(UpscalePass.Upscale(2f), UpscalePass.Sharpen(0.5f)),
    )

    @Test
    fun saveGeneratesUserIdAndPersistsAcrossInstances() {
        val settings = MapSettings()
        val saved = UserPresetStore(settings).save(profile())

        assertTrue(saved.id.startsWith("user-"))
        val reloaded = UserPresetStore(settings)
        assertEquals(listOf(saved), reloaded.presets.value)
    }

    @Test
    fun saveWithExistingIdUpdatesPresetInPlace() {
        val store = UserPresetStore(MapSettings())
        val created = store.save(profile(name = "First"))

        val updated = store.save(created.copy(name = "Second"))

        assertEquals(created.id, updated.id)
        assertEquals(listOf(updated), store.presets.value)
    }

    @Test
    fun saveNeverShadowsBuiltInOrAutoIds() {
        val store = UserPresetStore(MapSettings())

        val savedBuiltIn = store.save(profile(id = BuiltInPresets.ANIME_SD.id))
        val savedAuto = store.save(profile(id = "auto"))

        assertNotEquals(BuiltInPresets.ANIME_SD.id, savedBuiltIn.id)
        assertNotEquals("auto", savedAuto.id)
    }

    @Test
    fun savedPresetIsNeverBuiltInAndPassesAreClamped() {
        val store = UserPresetStore(MapSettings())

        val saved = store.save(
            UpscaleProfile(
                id = "",
                name = "  ",
                builtIn = true,
                passes = listOf(UpscalePass.Upscale(99f), UpscalePass.Sharpen(-1f), UpscalePass.Denoise(2f)),
            ),
        )

        assertEquals(false, saved.builtIn)
        assertTrue(saved.name.isNotBlank())
        assertEquals(
            listOf(
                UpscalePass.Upscale(UserPresetStore.UPSCALE_MAX),
                UpscalePass.Sharpen(0f),
                UpscalePass.Denoise(1f),
            ),
            saved.passes,
        )
    }

    @Test
    fun deleteRemovesPresetFromStoreAndPersistence() {
        val settings = MapSettings()
        val store = UserPresetStore(settings)
        val saved = store.save(profile())

        store.delete(saved.id)

        assertNull(store.byId(saved.id))
        assertTrue(UserPresetStore(settings).presets.value.isEmpty())
    }

    @Test
    fun exportImportRoundtripCreatesNewPresetWithSameChain() {
        val store = UserPresetStore(MapSettings())
        val original = store.save(profile(name = "Exported"))

        val imported = store.import(store.export(original)).getOrThrow()

        assertNotEquals(original.id, imported.id)
        assertEquals(original.name, imported.name)
        assertEquals(original.passes, imported.passes)
        assertEquals(2, store.presets.value.size)
    }

    @Test
    fun importOfGarbageFailsWithoutTouchingStore() {
        val store = UserPresetStore(MapSettings())

        val result = store.import("not json")

        assertTrue(result.isFailure)
        assertTrue(store.presets.value.isEmpty())
    }

    @Test
    fun corruptedPersistedPayloadDegradesToEmptyList() {
        val settings = MapSettings()
        settings.putString("user_presets", "{broken")

        assertTrue(UserPresetStore(settings).presets.value.isEmpty())
    }

    @Test
    fun presetsAreSortedByName() {
        val store = UserPresetStore(MapSettings())
        store.save(profile(name = "Bright"))
        store.save(profile(name = "Anime soft"))

        assertEquals(listOf("Anime soft", "Bright"), store.presets.value.map { it.name })
    }
}
