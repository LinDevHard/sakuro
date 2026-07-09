package com.rinwave.sakuro.engine.media3.usershader

import kotlin.math.exp
import kotlin.math.roundToInt

/** A graph pass with its fired hook, canonical frame slot and output resolution. */
internal data class PlannedPass(
    val pass: UserShaderPass,
    /** The raw hook point the pass fired on (`PREKERNEL`, `OUTPUT`, …) — its `HOOKED`. */
    val hook: String,
    /** The canonical frame slot of that hook (`MAIN`, `LINEAR`, `SIGMOID`, `POSTKERNEL`). */
    val stage: String,
    val outWidth: Int,
    val outHeight: Int,
)

/** Result of planning the graph: the executable passes in firing order. */
internal data class GraphPlan(
    val passes: List<PlannedPass>,
    val outputWidth: Int,
    val outputHeight: Int,
    /** DESCs of passes whose hooks never fire in this pipeline (mpv skips those too). */
    val skipped: List<String>,
    /** The frame slot the present pass reads (`MAIN`, or `POSTKERNEL` when post hooks ran). */
    val presentSlot: String = ShaderStages.MAIN,
    /** Accumulated `//!OFFSET` of the frame in pixels; compensated by the present pass. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/**
 * Statically runs a user-shader pass graph: expands `//!HOOK`s into firings in
 * mpv pipeline order (`NATIVE→MAIN→LINEAR→SIGMOID→PREKERNEL→POSTKERNEL→…→OUTPUT`,
 * document order within a hook point), computes the size of every intermediate
 * texture from the `//!WIDTH/HEIGHT` RPN formulas, cuts passes with a false
 * `//!WHEN`, and skips passes whose hooks never occur in the pipeline.
 *
 * `LINEAR`/`SIGMOID` hooks are emulated: synthetic conversion passes linearize
 * the frame before the first such hook and convert back before the next
 * electrical-space firing. `//!OFFSET` shifts accumulate on the frame and are
 * compensated by the present pass; `ALIGN` resets the accumulator.
 *
 * `OUTPUT` in RPN expressions is the target-size pseudo-texture (for gating
 * `//!WHEN`); hooking `OUTPUT` runs on the post-scale slot. Bare `//!PARAM`
 * names in expressions resolve to their default values.
 *
 * Features the executor does not support yet (`//!COMPUTE`, binds of
 * `//!TEXTURE`/`//!BUFFER` blocks) fail the plan with [UserShaderException] — the
 * runtime catches it and degrades the whole chain to passthrough instead of
 * silently rendering garbage.
 */
internal object ShaderGraphPlanner {

    fun plan(
        document: ShaderDocument,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ): GraphPlan {
        val builder = Builder(document, inputWidth, inputHeight, outputWidth, outputHeight)
        val skipped = mutableListOf<String>()
        for (firing in expandFirings(document.passes, skipped)) {
            builder.add(firing)
        }
        return builder.build(skipped)
    }

    private data class Firing(val pass: UserShaderPass, val hook: String)

    /**
     * One firing per hookable `//!HOOK` of every pass (mpv runs a pass at each
     * of its hook points that occurs), sorted by pipeline firing order; document
     * order is kept within a hook point. Passes with no hookable hooks are skipped.
     */
    private fun expandFirings(passes: List<UserShaderPass>, skipped: MutableList<String>): List<Firing> {
        val firings = mutableListOf<Firing>()
        for (pass in passes) {
            requireSupported(pass)
            val hooks = pass.hooks.distinct().filter { ShaderStages.isHookable(it) }
            if (hooks.isEmpty()) skipped += pass.desc
            hooks.forEach { firings += Firing(pass, it) }
        }
        return firings.sortedBy { ShaderStages.firingOrder(it.hook) } // stable sort
    }

    /** Rejects passes that need executor features not implemented yet (plan phase 5). */
    private fun requireSupported(pass: UserShaderPass) {
        if (pass.compute != null) {
            throw UserShaderException("pass '${pass.desc}' is a //!COMPUTE shader — not supported yet")
        }
    }

    /** Sequentially plans firings, materializing color states and the post slot on demand. */
    private class Builder(
        document: ShaderDocument,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
    ) {
        private val sizes = hashMapOf(
            ShaderStages.MAIN to (inputWidth to inputHeight),
            ShaderStages.OUTPUT_REF to (outputWidth to outputHeight),
            ShaderStages.NATIVE_CROPPED_REF to (inputWidth to inputHeight),
        )
        private val params = document.params.associate { it.name to it.defaultValue }
        private val unsupportedBinds =
            (document.textures.map { it.name } + document.buffers.map { it.name }).toSet()

        private val planned = mutableListOf<PlannedPass>()
        private var linearOpen = false
        private var sigmoidOpen = false
        private var postStarted = false
        private var offsetX = 0f
        private var offsetY = 0f

        fun add(firing: Firing) {
            val slot = ShaderStages.slotOf(firing.hook)
            when (slot) {
                ShaderStages.MAIN -> closeColorStates()
                ShaderStages.LINEAR -> sizes[ShaderStages.LINEAR] = sizes.getValue(ShaderStages.MAIN)
                ShaderStages.SIGMOID -> {
                    sizes[ShaderStages.LINEAR] = sizes.getValue(ShaderStages.MAIN)
                    sizes[ShaderStages.SIGMOID] = sizes.getValue(ShaderStages.MAIN)
                }
                ShaderStages.POST -> {
                    closeColorStates()
                    sizes.getOrPut(ShaderStages.POST) { sizes.getValue(ShaderStages.MAIN) }
                }
            }
            val pass = planPass(firing.pass, firing.hook, slot) ?: return
            openColorStates(slot)
            // The frame presents from the post slot only when a post pass actually ran.
            if (slot == ShaderStages.POST) postStarted = true
            planned += pass
            accumulateOffset(pass)
        }

        fun build(skipped: List<String>): GraphPlan {
            closeColorStates()
            val presentSlot = if (postStarted) ShaderStages.POST else ShaderStages.MAIN
            val (outW, outH) = sizes.getValue(presentSlot)
            return GraphPlan(planned, outW, outH, skipped, presentSlot, offsetX, offsetY)
        }

        /** Inserts linearize/sigmoidize before the first pass that needs the state. */
        private fun openColorStates(slot: String) {
            if ((slot == ShaderStages.LINEAR || slot == ShaderStages.SIGMOID) && !linearOpen) {
                planned += syntheticPass(SyntheticBody.LINEARIZE, ShaderStages.MAIN, ShaderStages.LINEAR)
                linearOpen = true
            }
            if (slot == ShaderStages.SIGMOID && !sigmoidOpen) {
                planned += syntheticPass(SyntheticBody.SIGMOIDIZE, ShaderStages.LINEAR, ShaderStages.SIGMOID)
                sigmoidOpen = true
            }
        }

        /** Converts the frame back to electrical space before MAIN/POST firings and present. */
        private fun closeColorStates() {
            if (sigmoidOpen) {
                planned += syntheticPass(SyntheticBody.DESIGMOIDIZE, ShaderStages.SIGMOID, ShaderStages.LINEAR)
                sigmoidOpen = false
            }
            if (linearOpen) {
                planned += syntheticPass(SyntheticBody.DELINEARIZE, ShaderStages.LINEAR, ShaderStages.MAIN)
                linearOpen = false
            }
        }

        /** A generated conversion pass between color states of the frame. */
        private fun syntheticPass(body: SyntheticBody, from: String, to: String): PlannedPass {
            val (width, height) = sizes.getValue(from)
            sizes[to] = width to height
            val pass = UserShaderPass(
                desc = body.desc,
                hooks = listOf(from),
                binds = listOf(from),
                save = to,
                width = null,
                height = null,
                components = 4,
                condition = null,
                offset = null,
                compute = null,
                body = body.glsl(from),
            )
            return PlannedPass(pass, hook = from, stage = from, outWidth = width, outHeight = height)
        }

        /** Plans one firing; null — its `//!WHEN` is false. Registers the SAVE size. */
        private fun planPass(pass: UserShaderPass, hook: String, slot: String): PlannedPass? {
            val resolve = valueResolver(pass, slot)
            if (pass.condition?.isTruthy(resolve) == false) return null

            validateBinds(pass, slot)

            val hookSize = sizes.getValue(slot)
            val outW = pass.width?.eval(resolve)?.roundToInt() ?: hookSize.first
            val outH = pass.height?.eval(resolve)?.roundToInt() ?: hookSize.second
            if (outW <= 0 || outH <= 0) {
                throw UserShaderException("non-positive size ${outW}x$outH for '${pass.desc}'")
            }

            val saveName = canonicalStage(pass.save, slot)
            if (saveName == slot && (outW to outH) != hookSize && !ShaderStages.isResizable(slot)) {
                throw UserShaderException(
                    "pass '${pass.desc}' resizes the non-resizable stage '$slot' (spec: only the MAIN family)",
                )
            }

            sizes[saveName] = outW to outH
            return PlannedPass(pass, hook, slot, outW, outH)
        }

        /**
         * Each input must be either a stage or the result of an earlier SAVE —
         * otherwise the pass would read a non-existent texture in drawFrame.
         */
        private fun validateBinds(pass: UserShaderPass, slot: String) {
            pass.binds.forEach { bind ->
                val name = canonicalStage(bind, slot)
                if (name in unsupportedBinds) {
                    throw UserShaderException(
                        "pass '${pass.desc}' binds '$name' declared as //!TEXTURE or //!BUFFER — not supported yet",
                    )
                }
                if (!sizes.containsKey(name)) {
                    throw UserShaderException("pass '${pass.desc}' binds an undefined texture '$name'")
                }
            }
        }

        /** Resolves `MAIN.w`-style size references (raw name first) and bare `//!PARAM` names. */
        private fun valueResolver(pass: UserShaderPass, slot: String): (String) -> Float = { token ->
            val dot = token.indexOf('.')
            if (dot <= 0) {
                params[token] ?: throw UserShaderException("unknown variable '$token' for '${pass.desc}'")
            } else {
                val name = token.substring(0, dot)
                // Raw first: `OUTPUT.w` is the target-size pseudo-texture, not the post slot.
                val size = sizes[name] ?: sizes[canonicalStage(name, slot)]
                    ?: throw UserShaderException("unknown texture '$token' for '${pass.desc}'")
                when (token.substring(dot + 1)) {
                    "w", "width" -> size.first.toFloat()
                    "h", "height" -> size.second.toFloat()
                    else -> throw UserShaderException("unknown field '$token' for '${pass.desc}'")
                }
            }
        }

        /** `//!OFFSET` of in-place frame writes accumulates; `ALIGN` resets it. */
        private fun accumulateOffset(planned: PlannedPass) {
            val offset = planned.pass.offset ?: return
            val savesFrame = canonicalStage(planned.pass.save, planned.stage) == planned.stage
            if (!savesFrame) return
            if (offset.align) {
                offsetX = 0f
                offsetY = 0f
            } else {
                offsetX += offset.x
                offsetY += offset.y
            }
        }
    }

    /**
     * `HOOKED` → the actually fired hook; hook-point names collapse to their
     * frame slot (`PREKERNEL`→`MAIN`, `SCALED`→`POSTKERNEL`); other names are kept as-is.
     */
    internal fun canonicalStage(name: String, hook: String): String {
        val resolved = if (name == MpvUserShaderParser.HOOKED) hook else name
        return ShaderStages.canonicalOrSelf(resolved)
    }

    // mpv sigmoid defaults (`sigmoid-center`, `sigmoid-slope`).
    private const val SIGMOID_CENTER = 0.75f
    private const val SIGMOID_SLOPE = 6.5f
    private val SIGMOID_OFFSET = 1f / (1f + exp(SIGMOID_SLOPE * SIGMOID_CENTER))
    private val SIGMOID_SCALE = 1f / (1f + exp(SIGMOID_SLOPE * (SIGMOID_CENTER - 1f))) - SIGMOID_OFFSET

    /** Bodies of the synthetic color-state conversion passes. */
    private enum class SyntheticBody(val desc: String) {
        LINEARIZE("<linearize>") {
            override fun glsl(from: String): String =
                "vec4 hook() { return linearize(clamp(${from}_tex(${from}_pos), 0.0, 1.0)); }"
        },
        DELINEARIZE("<delinearize>") {
            override fun glsl(from: String): String =
                "vec4 hook() { return delinearize(clamp(${from}_tex(${from}_pos), 0.0, 1.0)); }"
        },
        SIGMOIDIZE("<sigmoidize>") {
            // mpv pass_sigmoidize: v = center - log(1/(x*scale + offset) - 1)/slope (x is linear).
            override fun glsl(from: String): String = """
                vec4 hook() {
                  vec4 c = clamp(${from}_tex(${from}_pos), 0.0, 1.0);
                  c.rgb = vec3($SIGMOID_CENTER) -
                      log(vec3(1.0) / (c.rgb * $SIGMOID_SCALE + $SIGMOID_OFFSET) - vec3(1.0)) / $SIGMOID_SLOPE;
                  return c;
                }
            """.trimIndent()
        },
        DESIGMOIDIZE("<desigmoidize>") {
            // mpv pass_unsigmoidize: x = (1/(1 + exp(slope*(center - v))) - offset)/scale.
            override fun glsl(from: String): String = """
                vec4 hook() {
                  vec4 c = ${from}_tex(${from}_pos);
                  c.rgb = (vec3(1.0) / (vec3(1.0) + exp(vec3($SIGMOID_SLOPE) * (vec3($SIGMOID_CENTER) - c.rgb))) -
                      $SIGMOID_OFFSET) / $SIGMOID_SCALE;
                  return c;
                }
            """.trimIndent()
        },
        ;

        abstract fun glsl(from: String): String
    }
}
