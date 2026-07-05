package com.rinwave.sakuro.engine.media3.anime4k

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the REAL vendored Anime4K `.glsl` files (assets/anime4k from engine-mpv)
 * through the parser and planner — checks that the generic runtime handles all 6
 * shaders, including the wide M models (~300 lines, conv2d_1..6, CReLU) and
 * Denoise with the PREKERNEL/LINELUMA/STATSMAX stages and `COMPONENTS 1`.
 *
 * Reads files from the repository relative to the module (the unit test's
 * working directory = the module directory).
 */
class RealShaderGraphTest {

    private val assetsDir: File = locateAssets()

    private fun locateAssets(): File {
        // Look for anime4k upward from the test's working directory (the module directory).
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "engine/engine-mpv/src/main/assets/anime4k")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        fail("assets/anime4k directory not found relative to ${File("").absolutePath}")
    }

    private fun source(file: String): String = File(assetsDir, file).readText()

    private val allShaders = listOf(
        "Anime4K_Clamp_Highlights.glsl",
        "Anime4K_Denoise_Bilateral_Mode.glsl",
        "Anime4K_Restore_CNN_S.glsl",
        "Anime4K_Restore_CNN_M.glsl",
        "Anime4K_Upscale_CNN_x2_S.glsl",
        "Anime4K_Upscale_CNN_x2_M.glsl",
    )

    @Test
    fun `all six shaders parse into non-empty passes`() {
        for (file in allShaders) {
            val passes = MpvUserShaderParser.parse(source(file))
            assertTrue(passes.isNotEmpty(), "$file produced no passes")
            assertTrue(passes.all { it.body.contains("hook()") }, "$file: a pass has no hook()")
        }
    }

    @Test
    fun `the full anime chain Clamp→Restore→Upscale plans and yields ×2`() {
        // As Anime4KChain builds it for an ANIME profile with Sharpen+Upscale.
        val chainFiles = listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        )
        val passes = chainFiles.flatMap { MpvUserShaderParser.parse(source(it)) }
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640 * 4, 360 * 4)

        assertTrue(plan.passes.isNotEmpty())
        // The upscale branch is active (OUTPUT ≫ MAIN) → depth-to-space doubles MAIN.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `the M upscale model plans without errors and doubles MAIN`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Upscale_CNN_x2_M.glsl"))
        val plan = Anime4KGraphPlanner.plan(passes, 720, 480, 720 * 4, 480 * 4)
        assertEquals(1440 to 960, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `Denoise with PREKERNEL stages and COMPONENTS 1 plans, MAIN size unchanged`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Denoise_Bilateral_Mode.glsl"))
        assertTrue(passes.isNotEmpty())
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640 * 4, 360 * 4)
        // Denoise does not scale.
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `without upscale (OUTPUT equals input) depth-to-space is cut, MAIN stays source`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Upscale_CNN_x2_S.glsl"))
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640, 360)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `a fragment shader is generated for every real pass`() {
        for (file in allShaders) {
            for (pass in MpvUserShaderParser.parse(source(file))) {
                val fragment = ShaderPreamble.fragmentShader(pass, isFinal = false)
                assertTrue(fragment.startsWith("#version 300 es"), "$file: no version in the shim")
                assertTrue(fragment.contains("void main()"), "$file: no main() in the shim")
            }
        }
    }

    // Every `<name>_tex/_texOff/_pos/_pt/_size` from the body must be declared in
    // the shim — otherwise the shader fails to compile (Denoise bug: BIND HOOKED, body calls
    // MAIN_texOff). Catches missing stage aliases across all real shaders.
    private val symbolRef = Regex("([A-Za-z_][A-Za-z0-9_]*)_(texOff|tex|pos|pt|size|mul)\\b")

    @Test
    fun `Clamp with BIND HOOKED and a MAIN_texOff body gets the MAIN alias`() {
        // This exact pass (De-Ring-Compute-Statistics: HOOK MAIN, BIND HOOKED,
        // body calls MAIN_texOff) used to break compilation on device → passthrough.
        val stats = MpvUserShaderParser.parse(source("Anime4K_Clamp_Highlights.glsl"))
            .first { it.body.contains("MAIN_texOff") }
        val fragment = ShaderPreamble.fragmentShader(stats, isFinal = false)
        assertTrue(fragment.contains("vec4 MAIN_tex("), "no MAIN_tex alias")
        assertTrue(fragment.contains("vec4 MAIN_texOff("), "no MAIN_texOff alias")
    }

    @Test
    fun `all mpv symbols from real shader bodies are declared in the shim`() {
        var checked = 0
        for (file in allShaders) {
            for (pass in MpvUserShaderParser.parse(source(file))) {
                val fragment = ShaderPreamble.fragmentShader(pass, isFinal = false)
                val referenced = symbolRef.findAll(pass.body).map { it.groupValues[1] }.toSet()
                for (name in referenced) {
                    checked++
                    // Any declared name (bind or alias) always gets `<name>_tex(`.
                    assertTrue(
                        fragment.contains("vec4 ${name}_tex(") || fragment.contains("#define ${name}_pos"),
                        "$file / '${pass.desc}': symbol '${name}_*' not declared in the shim",
                    )
                }
            }
        }
        // Guard against a degenerate test: symbols were actually extracted and checked.
        assertTrue(checked > 20, "too few symbols checked ($checked) — the regex did not match")
    }
}
