package com.rinwave.sakuro.engine.media3.anime4k

import kotlin.math.roundToInt

/** A graph pass with its output resolution already computed. */
internal data class PlannedPass(
    val pass: UserShaderPass,
    val outWidth: Int,
    val outHeight: Int,
)

/** Result of planning the graph: the active passes and the final MAIN size. */
internal data class GraphPlan(
    val passes: List<PlannedPass>,
    val outputWidth: Int,
    val outputHeight: Int,
)

/**
 * Statically runs the Anime4K pass graph: from the input frame size it computes
 * the size of every intermediate texture from the `//!WIDTH/HEIGHT` RPN formulas,
 * cuts passes with a false `//!WHEN`, and returns
 * the final size of the `MAIN` stage (for anime upscaling — usually ×2).
 *
 * Size resolution runs over "stages" (`MAIN`, `PREKERNEL`, `HOOKED`) and
 * named intermediates (`conv2d_tf`…). `OUTPUT` is the target surface size
 * (for gating `//!WHEN`, where upscale turns on only if the output is larger than the input).
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
        // Frame stages (MAIN/PREKERNEL/NATIVE) are the same evolving
        // frame: we have no real scaler between them, and the PREKERNEL (Clamp) hook
        // must affect what later MAIN passes read. We canonicalize them to MAIN.
        val sizes = hashMapOf(
            MAIN to (inputWidth to inputHeight),
            "OUTPUT" to (outputWidth to outputHeight),
        )

        val planned = mutableListOf<PlannedPass>()
        for (pass in passes) {
            val hook = canonicalStage(pass.hook, pass.hook)
            val hookSize = sizes[hook]
                ?: error("Anime4K: pass '${pass.desc}' hooks an unknown stage '${pass.hook}'")
            val resolve = sizeResolver(pass, sizes)

            if (pass.condition?.isTruthy(resolve) == false) continue

            // Each input must be either a stage or the result of an earlier SAVE —
            // otherwise the pass would read a non-existent texture in drawFrame.
            pass.binds.forEach { bind ->
                val name = canonicalStage(bind, pass.hook)
                require(sizes.containsKey(name)) {
                    "Anime4K: pass '${pass.desc}' binds an undefined texture '$name'"
                }
            }

            val outW = pass.width?.eval(resolve)?.roundToInt() ?: hookSize.first
            val outH = pass.height?.eval(resolve)?.roundToInt() ?: hookSize.second
            require(outW > 0 && outH > 0) { "Anime4K: non-positive size ${outW}x$outH for '${pass.desc}'" }

            sizes[canonicalStage(pass.save, pass.hook)] = outW to outH
            planned += PlannedPass(pass, outW, outH)
        }

        val (finalW, finalH) = sizes.getValue(MAIN)
        return GraphPlan(planned, finalW, finalH)
    }

    /** Resolves tokens like `MAIN.w`/`HOOKED.h` into numeric texture sizes. */
    private fun sizeResolver(pass: UserShaderPass, sizes: Map<String, Pair<Int, Int>>): (String) -> Float = { token ->
        val dot = token.indexOf('.')
        require(dot > 0) { "Anime4K: not a size reference '$token' for '${pass.desc}'" }
        val size = sizes[canonicalStage(token.substring(0, dot), pass.hook)]
            ?: error("Anime4K: unknown texture '$token' for '${pass.desc}'")
        when (val field = token.substring(dot + 1)) {
            "w" -> size.first.toFloat()
            "h" -> size.second.toFloat()
            else -> error("Anime4K: unknown field '$field' for '${pass.desc}'")
        }
    }

    /**
     * `HOOKED` → the actually hooked stage; the frame stages `PREKERNEL`/`NATIVE`
     * are canonicalized to `MAIN` (one evolving frame); other names are kept as-is.
     */
    internal fun canonicalStage(name: String, hook: String): String {
        val resolved = if (name == MpvUserShaderParser.HOOKED) hook else name
        return if (resolved == "PREKERNEL" || resolved == "NATIVE") MAIN else resolved
    }
}
