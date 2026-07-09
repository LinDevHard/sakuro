package com.rinwave.sakuro.engine.media3.usershader

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

    private fun document(file: String): ShaderDocument =
        MpvUserShaderParser.parse(File(assetsDir, file).readText())

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
            val passes = document(file).passes
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
        val merged = ShaderDocument.merge(chainFiles.map { document(it) })
        val plan = ShaderGraphPlanner.plan(merged, 640, 360, 640 * 4, 360 * 4)

        assertTrue(plan.passes.isNotEmpty())
        // The upscale branch is active (OUTPUT ≫ MAIN) → depth-to-space doubles MAIN.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
        // mpv pipeline order: the De-Ring-Clamp hooks PREKERNEL and must fire after
        // all MAIN hooks (Restore/Upscale), even though its file is first in the chain.
        assertEquals("Anime4K-v4.0-De-Ring-Clamp", plan.passes.last().pass.desc)
        assertEquals(1280 to 720, plan.passes.last().outWidth to plan.passes.last().outHeight)
    }

    @Test
    fun `the M upscale model plans without errors and doubles MAIN`() {
        val plan = ShaderGraphPlanner.plan(document("Anime4K_Upscale_CNN_x2_M.glsl"), 720, 480, 720 * 4, 480 * 4)
        assertEquals(1440 to 960, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `Denoise with PREKERNEL stages and COMPONENTS 1 plans, MAIN size unchanged`() {
        val denoise = document("Anime4K_Denoise_Bilateral_Mode.glsl")
        assertTrue(denoise.passes.isNotEmpty())
        val plan = ShaderGraphPlanner.plan(denoise, 640, 360, 640 * 4, 360 * 4)
        // Denoise does not scale.
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `without upscale (OUTPUT equals input) depth-to-space is cut, MAIN stays source`() {
        val plan = ShaderGraphPlanner.plan(document("Anime4K_Upscale_CNN_x2_S.glsl"), 640, 360, 640, 360)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `no real pass is skipped — every Anime4K hook fires in our pipeline`() {
        for (file in allShaders) {
            val plan = ShaderGraphPlanner.plan(document(file), 640, 360, 640 * 4, 360 * 4)
            assertTrue(plan.skipped.isEmpty(), "$file: unexpectedly skipped ${plan.skipped}")
        }
    }

    @Test
    fun `a fragment shader is generated for every real pass`() {
        for (file in allShaders) {
            for (pass in document(file).passes) {
                val fragment = ShaderPreamble.fragmentShader(pass)
                assertTrue(fragment.startsWith("#version 300 es"), "$file: no version in the shim")
                assertTrue(fragment.contains("void main()"), "$file: no main() in the shim")
            }
        }
    }

    // Every `<name>_tex/_texOff/_pos/_pt/_size/_mul` from the body must be declared in
    // the shim — otherwise the shader fails to compile (Denoise bug: BIND HOOKED, body calls
    // MAIN_texOff). Catches missing stage aliases across all real shaders. The rarer
    // suffixes (_raw/_off/_rot/_map) are excluded: Anime4K macro parameters like
    // `x_off` would false-positive; their declaration is covered by ShaderPreambleTest.
    private val symbolRef = Regex("([A-Za-z_][A-Za-z0-9_]*)_(texOff|tex|pos|pt|size|mul)\\b")

    @Test
    fun `Clamp with BIND HOOKED and a MAIN_texOff body gets the MAIN alias`() {
        // This exact pass (De-Ring-Compute-Statistics: HOOK MAIN, BIND HOOKED,
        // body calls MAIN_texOff) used to break compilation on device → passthrough.
        val stats = document("Anime4K_Clamp_Highlights.glsl").passes
            .first { it.body.contains("MAIN_texOff") }
        val fragment = ShaderPreamble.fragmentShader(stats)
        assertTrue(fragment.contains("vec4 MAIN_tex("), "no MAIN_tex alias")
        assertTrue(fragment.contains("#define MAIN_texOff(off)"), "no MAIN_texOff alias")
    }

    @Test
    fun `all mpv symbols from real shader bodies are declared in the shim`() {
        var checked = 0
        for (file in allShaders) {
            for (pass in document(file).passes) {
                val fragment = ShaderPreamble.fragmentShader(pass)
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
