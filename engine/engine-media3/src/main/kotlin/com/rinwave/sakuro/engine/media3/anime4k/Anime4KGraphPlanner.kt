package com.rinwave.sakuro.engine.media3.anime4k

import kotlin.math.roundToInt

/** Проход графа с уже вычисленным разрешением выхода. */
internal data class PlannedPass(
    val pass: UserShaderPass,
    val outWidth: Int,
    val outHeight: Int,
)

/** Результат планирования графа: активные проходы и итоговый размер MAIN. */
internal data class GraphPlan(
    val passes: List<PlannedPass>,
    val outputWidth: Int,
    val outputHeight: Int,
)

/**
 * Статически прогоняет граф проходов (docs/anime4k-media3-port-plan.md §3.2):
 * от размера входного кадра вычисляет размер каждой промежуточной текстуры по
 * RPN-формулам `//!WIDTH/HEIGHT`, отсекает проходы с ложным `//!WHEN` и отдаёт
 * итоговый размер стадии `MAIN` (для аниме-апскейла — обычно ×2).
 *
 * Резолвинг размеров идёт по «стадиям» (`MAIN`, `PREKERNEL`, `HOOKED`) и
 * именованным промежуткам (`conv2d_tf`…). `OUTPUT` — целевой размер поверхности
 * (для гейтинга `//!WHEN`, где апскейл включается лишь если выход крупнее входа).
 */
internal object Anime4KGraphPlanner {

    private const val MAIN = "MAIN"

    fun plan(
        passes: List<UserShaderPass>,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): GraphPlan {
        // Стадии-кадры (MAIN/PREKERNEL/NATIVE) — один и тот же эволюционирующий
        // кадр: реального скейлера между ними у нас нет, а хук PREKERNEL (Clamp)
        // должен влиять на то, что дальше читают MAIN-проходы. Канонизируем в MAIN.
        val sizes = hashMapOf(
            MAIN to (inputWidth to inputHeight),
            "OUTPUT" to (outputWidth to outputHeight),
        )

        val planned = mutableListOf<PlannedPass>()
        for (pass in passes) {
            val hook = canonicalStage(pass.hook, pass.hook)
            val hookSize = sizes[hook]
                ?: error("Anime4K: проход '${pass.desc}' хукает неизвестную стадию '${pass.hook}'")
            val resolve = sizeResolver(pass, sizes)

            if (pass.condition?.isTruthy(resolve) == false) continue

            // Каждый вход должен быть либо стадией, либо результатом раннего SAVE —
            // иначе в drawFrame проход прочитает несуществующую текстуру.
            pass.binds.forEach { bind ->
                val name = canonicalStage(bind, pass.hook)
                require(sizes.containsKey(name)) {
                    "Anime4K: проход '${pass.desc}' биндит неопределённую текстуру '$name'"
                }
            }

            val outW = pass.width?.eval(resolve)?.roundToInt() ?: hookSize.first
            val outH = pass.height?.eval(resolve)?.roundToInt() ?: hookSize.second
            require(outW > 0 && outH > 0) { "Anime4K: неположительный размер ${outW}x$outH в '${pass.desc}'" }

            sizes[canonicalStage(pass.save, pass.hook)] = outW to outH
            planned += PlannedPass(pass, outW, outH)
        }

        val (finalW, finalH) = sizes.getValue(MAIN)
        return GraphPlan(planned, finalW, finalH)
    }

    /** Резолвер токенов вида `MAIN.w`/`HOOKED.h` в числовые размеры текстур. */
    private fun sizeResolver(pass: UserShaderPass, sizes: Map<String, Pair<Int, Int>>): (String) -> Float = { token ->
        val dot = token.indexOf('.')
        require(dot > 0) { "Anime4K: не размерная ссылка '$token' в '${pass.desc}'" }
        val size = sizes[canonicalStage(token.substring(0, dot), pass.hook)]
            ?: error("Anime4K: неизвестная текстура '$token' в '${pass.desc}'")
        when (val field = token.substring(dot + 1)) {
            "w" -> size.first.toFloat()
            "h" -> size.second.toFloat()
            else -> error("Anime4K: неизвестное поле '$field' в '${pass.desc}'")
        }
    }

    /**
     * `HOOKED` → реально хукнутая стадия; стадии-кадры `PREKERNEL`/`NATIVE`
     * канонизируются в `MAIN` (один эволюционирующий кадр); прочие имена как есть.
     */
    internal fun canonicalStage(name: String, hook: String): String {
        val resolved = if (name == MpvUserShaderParser.HOOKED) hook else name
        return if (resolved == "PREKERNEL" || resolved == "NATIVE") MAIN else resolved
    }
}
