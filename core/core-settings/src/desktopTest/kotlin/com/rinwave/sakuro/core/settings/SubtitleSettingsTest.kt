package com.rinwave.sakuro.core.settings

import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.player.SubtitlePreferences
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleSettingsTest {
    @Test
    fun `subtitle preferences survive a new settings instance`() {
        val node = Preferences.userRoot().node("sakuro-test-${UUID.randomUUID()}")
        try {
            val storage = PreferencesSettings(node)
            val settings = SakuroSettings(EngineType.MEDIA3, storage)
            val expected = SubtitlePreferences(28f, false, 0.2f, -1500L, "ru")
            settings.setSubtitlePreferences(expected)
            assertEquals(expected, settings.subtitlePreferences.value)
            assertEquals(expected, SakuroSettings(EngineType.MEDIA3, storage).subtitlePreferences.value)
        } finally {
            node.removeNode()
        }
    }
}
