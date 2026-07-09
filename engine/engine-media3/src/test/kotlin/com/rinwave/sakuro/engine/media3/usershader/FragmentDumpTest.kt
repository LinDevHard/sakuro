package com.rinwave.sakuro.engine.media3.usershader

import java.io.File
import kotlin.test.Test

/**
 * Dumps every fragment shader the runtime would generate (real fixture chains
 * plus the synthetic plane/color passes) so they can be compiled offline with a
 * GLSL validator, e.g.:
 *
 * ```
 * FRAGMENT_DUMP_DIR=/tmp/frags ./gradlew :engine:engine-media3:testDebugUnitTest
 * cd /tmp/frags && for f in ./?*.frag; do glslc -fshader-stage=fragment --target-env=opengl "$f" -o /dev/null; done
 * ```
 *
 * A no-op unless the `FRAGMENT_DUMP_DIR` environment variable is set.
 */
class FragmentDumpTest {

    @Test
    fun `dump generated fragments for offline GLSL validation`() {
        val dir = System.getenv("FRAGMENT_DUMP_DIR")?.let(::File) ?: return
        dir.mkdirs()

        val fixtures = listOf(
            "adaptive-sharpen.glsl",
            "SSimSuperRes.glsl",
            "FSRCNNX_x2_8-0-4-1.glsl",
            "KrigBilateral.glsl",
        )
        for (fixture in fixtures) {
            val source = javaClass.getResourceAsStream("/usershader/$fixture")!!
                .bufferedReader().use { it.readText() }
            val document = MpvUserShaderParser.parse(source)
            val plan = ShaderGraphPlanner.plan(document, 640, 360, 640 * 4, 360 * 4)
            plan.passes.forEachIndexed { i, planned ->
                val fragment = ShaderPreamble.fragmentShader(planned.pass, planned.hook, document.params)
                val name = fixture.removeSuffix(".glsl") + "_$i" +
                    "_" + planned.pass.desc.replace(Regex("[^A-Za-z0-9]+"), "-")
                File(dir, "$name.frag").writeText(fragment)
            }
        }
    }
}
