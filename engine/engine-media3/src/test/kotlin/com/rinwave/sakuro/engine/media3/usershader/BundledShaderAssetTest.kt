package com.rinwave.sakuro.engine.media3.usershader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The vendored `assets/shaders_bundled` files must parse and plan on the
 * generic runtime (the ravu hooks are also covered as test fixtures in
 * [ThirdPartyShaderTest]; here the shipped copies are exercised).
 */
class BundledShaderAssetTest {

    private fun document(name: String): ShaderDocument {
        // Unit tests run with the module directory as the working directory.
        val file = File("src/main/assets/shaders_bundled/$name")
        if (!file.isFile) fail("bundled asset $name not found at ${file.absolutePath}")
        return MpvUserShaderParser.parse(file.readText())
    }

    @Test
    fun `CAS plans both LUMA passes unconditionally at the frame size`() {
        val document = document("CAS.glsl")
        assertEquals(2, document.passes.size)
        assertTrue(document.passes.all { it.hooks == listOf("LUMA") })
        assertEquals("SHARPENING", document.params.single().name)

        // No upscale in the pipeline — the sharpening must still fire (the
        // upstream no-scale WHEN gates are removed in our vendored copy).
        val plan = ShaderGraphPlanner.plan(document, 1920, 1080, 1920, 1080)
        assertTrue(plan.skipped.isEmpty(), "skipped: ${plan.skipped}")
        assertEquals(
            listOf(
                "<extract-luma>", "<extract-chroma>",
                "FidelityFX Sharpening (Relinearization)", "FidelityFX Sharpening",
                "<merge-planes>",
            ),
            plan.passes.map { it.pass.desc },
        )
        assertEquals(1920 to 1080, plan.outputWidth to plan.outputHeight)

        val sharpening = plan.passes[3]
        val defaults = ShaderPreamble.fragmentShader(sharpening.pass, sharpening.hook, document)
        assertTrue(defaults.contains("const float SHARPENING = 0.0;"), defaults)
        val tuned = ShaderPreamble.fragmentShader(
            sharpening.pass, sharpening.hook, document,
            paramValues = mapOf("SHARPENING" to 0.8f),
        )
        assertTrue(tuned.contains("const float SHARPENING = 0.8;"), tuned)
    }

    @Test
    fun `the Sakuro bilateral denoise plans one MAIN pass with a tunable intensity`() {
        val document = document("Sakuro_Denoise_Bilateral.glsl")
        val pass = document.passes.single()
        assertEquals(listOf("MAIN"), pass.hooks)
        val param = document.params.single()
        assertEquals("intensity", param.name)
        assertEquals(0f to 1f, param.minimum to param.maximum)

        val plan = ShaderGraphPlanner.plan(document, 1280, 720, 1280, 720)
        assertEquals(1, plan.passes.size)
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)

        val fragment = ShaderPreamble.fragmentShader(
            pass, document = document,
            paramValues = mapOf("intensity" to 0.6f),
        )
        assertTrue(fragment.contains("const float intensity = 0.6;"), fragment)
    }

    @Test
    fun `no bundled shader hides a directive marker inside a comment`() {
        // Classic mpv vo=gpu (what engine-mpv runs) scans for "//!" anywhere in
        // a line, so a marker mentioned inside a comment is parsed as a real
        // directive and the whole shader is rejected — silently, at render time.
        val dir = File("src/main/assets/shaders_bundled")
        for (file in dir.listFiles().orEmpty()) {
            file.readLines().forEachIndexed { index, line ->
                val marker = line.indexOf("//!")
                if (marker > 0) {
                    fail("${file.name}:${index + 1} mentions a directive marker mid-line: $line")
                }
            }
        }
    }

    @Test
    fun `the shipped ravu copies match the test fixtures`() {
        for (name in listOf("ravu-r3.hook", "ravu-lite-r3.hook")) {
            val shipped = File("src/main/assets/shaders_bundled/$name")
            val fixture = File("src/test/resources/usershader/$name")
            assertEquals(fixture.readText(), shipped.readText(), "$name differs from the fixture")
        }
    }
}
