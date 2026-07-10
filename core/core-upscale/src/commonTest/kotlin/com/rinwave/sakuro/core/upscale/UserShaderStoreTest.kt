package com.rinwave.sakuro.core.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserShaderStoreTest {

    @Test
    fun `sanitizeName strips paths and unsafe characters and keeps shader extensions`() {
        assertEquals("FSRCNNX_x2_8-0-4-1.glsl", UserShaderStore.sanitizeName("FSRCNNX_x2_8-0-4-1.glsl"))
        assertEquals("ravu-r3.hook", UserShaderStore.sanitizeName("/sdcard/Download/ravu-r3.hook"))
        // Path components are dropped entirely — only the basename survives.
        assertEquals("passwd.glsl", UserShaderStore.sanitizeName("evil/../../passwd"))
        assertEquals("shader.glsl", UserShaderStore.sanitizeName("///"))
        // A name with no safe characters falls back to the default.
        assertEquals("shader.glsl", UserShaderStore.sanitizeName("шейдер"))
    }

    @Test
    fun `a chain-only profile counts as enabled`() {
        val profile = UpscaleProfile(id = "user-1", name = "FSRCNNX", shaderChain = listOf("FSRCNNX.glsl"))
        assertTrue(profile.isEnabled)
        assertTrue(profile.passes.isEmpty())
    }

    @Test
    fun `the codec round-trips the shader chain and old payloads decode without it`() {
        val profile = UpscaleProfile(
            id = "user-2",
            name = "ravu",
            passes = listOf(UpscalePass.Upscale(2f)),
            shaderChain = listOf("ravu-r3.hook", "KrigBilateral.glsl"),
        )
        val decoded = ProfileCodec.decode(ProfileCodec.encode(profile)).getOrThrow()
        assertEquals(profile.shaderChain, decoded.shaderChain)

        // A pre-chain export (no field) still decodes.
        val legacy = """{"id":"user-3","name":"Old","passes":[]}"""
        assertEquals(emptyList(), ProfileCodec.decode(legacy).getOrThrow().shaderChain)
    }

    @Test
    fun `the preset store sanitizes chain entries to safe file names`() {
        val store = UserPresetStore(settings = com.russhwolf.settings.MapSettings())
        val saved = store.save(
            UpscaleProfile(
                id = "",
                name = "Chained",
                shaderChain = listOf("/etc/evil.glsl", "a.glsl", "a.glsl") + List(30) { "s$it.glsl" },
            ),
        )
        assertTrue(saved.shaderChain.none { it.contains('/') })
        assertEquals(saved.shaderChain, saved.shaderChain.distinct())
        assertTrue(saved.shaderChain.size <= UserShaderStore.MAX_CHAIN)
    }
}
