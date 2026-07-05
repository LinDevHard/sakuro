package com.rinwave.sakuro.engine.media3.anime4k

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Прогоняет РЕАЛЬНЫЕ вендоренные Anime4K `.glsl` (assets/anime4k из engine-mpv)
 * через парсер и планировщик — проверка, что дженерик-рантайм тянет все 6
 * шейдеров, включая широкие M-модели (~300 строк, conv2d_1..6, CReLU) и
 * Denoise со стадиями PREKERNEL/LINELUMA/STATSMAX и `COMPONENTS 1`.
 *
 * Читает файлы из репозитория относительно модуля (рабочая директория
 * unit-теста = каталог модуля).
 */
class RealShaderGraphTest {

    private val assetsDir: File = locateAssets()

    private fun locateAssets(): File {
        // Ищем anime4k вверх от рабочей директории теста (каталог модуля).
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "engine/engine-mpv/src/main/assets/anime4k")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        fail("не найден каталог assets/anime4k относительно ${File("").absolutePath}")
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
    fun `все шесть шейдеров парсятся в непустые проходы`() {
        for (file in allShaders) {
            val passes = MpvUserShaderParser.parse(source(file))
            assertTrue(passes.isNotEmpty(), "$file не дал проходов")
            assertTrue(passes.all { it.body.contains("hook()") }, "$file: у прохода нет hook()")
        }
    }

    @Test
    fun `полная аниме-цепочка Clamp→Restore→Upscale планируется и даёт ×2`() {
        // Как собирает Anime4KChain для профиля ANIME с Sharpen+Upscale.
        val chainFiles = listOf(
            "Anime4K_Clamp_Highlights.glsl",
            "Anime4K_Restore_CNN_S.glsl",
            "Anime4K_Upscale_CNN_x2_S.glsl",
        )
        val passes = chainFiles.flatMap { MpvUserShaderParser.parse(source(it)) }
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640 * 4, 360 * 4)

        assertTrue(plan.passes.isNotEmpty())
        // Upscale-ветвь активна (OUTPUT ≫ MAIN) → depth-to-space удваивает MAIN.
        assertEquals(1280 to 720, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `M-модель апскейла планируется без ошибок и удваивает MAIN`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Upscale_CNN_x2_M.glsl"))
        val plan = Anime4KGraphPlanner.plan(passes, 720, 480, 720 * 4, 480 * 4)
        assertEquals(1440 to 960, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `Denoise со стадиями PREKERNEL и COMPONENTS 1 планируется, размер MAIN не меняется`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Denoise_Bilateral_Mode.glsl"))
        assertTrue(passes.isNotEmpty())
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640 * 4, 360 * 4)
        // Деноиз не масштабирует.
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `без апскейла (OUTPUT равен входу) депт-ту-спейс отсекается, MAIN исходный`() {
        val passes = MpvUserShaderParser.parse(source("Anime4K_Upscale_CNN_x2_S.glsl"))
        val plan = Anime4KGraphPlanner.plan(passes, 640, 360, 640, 360)
        assertEquals(640 to 360, plan.outputWidth to plan.outputHeight)
    }

    @Test
    fun `фрагментный шейдер генерится для каждого реального прохода`() {
        for (file in allShaders) {
            for (pass in MpvUserShaderParser.parse(source(file))) {
                val fragment = ShaderPreamble.fragmentShader(pass, isFinal = false)
                assertTrue(fragment.startsWith("#version 300 es"), "$file: нет версии в шиме")
                assertTrue(fragment.contains("void main()"), "$file: нет main() в шиме")
            }
        }
    }
}
