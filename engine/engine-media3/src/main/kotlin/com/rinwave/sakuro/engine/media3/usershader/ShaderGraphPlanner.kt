package com.rinwave.sakuro.engine.media3.usershader

import kotlin.math.exp
import kotlin.math.roundToInt

/** A graph pass with its fired hook, canonical frame slot and output resolution. */
internal data class PlannedPass(
    val pass: UserShaderPass,
    /** The raw hook point the pass fired on (`PREKERNEL`, `OUTPUT`, …) — its `HOOKED`. */
    val hook: String,
    /** The canonical frame slot of that hook (`MAIN`, `LUMA`, `LINEAR`, `POSTKERNEL`, …). */
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
 * mpv pipeline order (`LUMA→CHROMA→…→MAIN→LINEAR→SIGMOID→PREKERNEL→POSTKERNEL→…`,
 * document order within a hook point), computes the size of every intermediate
 * texture from the `//!WIDTH/HEIGHT` RPN formulas, cuts passes with a false
 * `//!WHEN`, and skips passes whose hooks never occur in the pipeline.
 *
 * `LUMA`/`CHROMA` hooks run on virtual planes synthesized from the RGB frame
 * (full-res Y and half-res CbCr, BT.709 full-range) and merged back into `MAIN`
 * at the final LUMA size — this is how FSRCNNX-style luma doublers upscale the
 * frame. `LINEAR`/`SIGMOID` hooks are emulated with synthetic conversion passes
 * around them. `//!OFFSET` shifts accumulate on the frame and are compensated by
 * the present pass; `ALIGN` resets the accumulator.
 *
 * `OUTPUT` in RPN expressions is the target-size pseudo-texture (for gating
 * `//!WHEN`); hooking `OUTPUT` runs on the post-scale slot. Bare `//!PARAM`
 * names in expressions resolve to their default values.
 *
 * `//!TEXTURE` blocks are sized statically and validated at bind time (see
 * [ShaderTextureFormats]); the program uploads them as LUTs, creates
 * `STORAGE` images, and allocates `//!BUFFER` blocks (compute passes on
 * ES 3.1 only). Anything the device cannot run fails the plan with
 * [UserShaderException] — the runtime catches it and degrades the whole
 * chain to passthrough instead of silently rendering garbage.
 */
internal object ShaderGraphPlanner {

    @Suppress("LongParameterList")
    fun plan(
        document: ShaderDocument,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        caps: RuntimeCapabilities = RuntimeCapabilities.BASELINE,
    ): GraphPlan {
        val builder = Builder(document, inputWidth, inputHeight, outputWidth, outputHeight, caps)
        val skipped = mutableListOf<String>()
        for (firing in expandFirings(document.passes, skipped, caps)) {
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
    private fun expandFirings(
        passes: List<UserShaderPass>,
        skipped: MutableList<String>,
        caps: RuntimeCapabilities,
    ): List<Firing> {
        val firings = mutableListOf<Firing>()
        for (pass in passes) {
            requireSupported(pass, caps)
            val hooks = pass.hooks.distinct().filter { ShaderStages.isHookable(it) }
            if (hooks.isEmpty()) skipped += pass.desc
            hooks.forEach { firings += Firing(pass, it) }
        }
        return firings.sortedBy { ShaderStages.firingOrder(it.hook) } // stable sort
    }

    /** Rejects passes the executor cannot run on this device. */
    private fun requireSupported(pass: UserShaderPass, caps: RuntimeCapabilities) {
        if (pass.compute != null && !caps.compute) {
            throw UserShaderException("pass '${pass.desc}' is a //!COMPUTE shader — needs an ES 3.1 context")
        }
    }

    /** Sequentially plans firings, materializing planes, color states and the post slot on demand. */
    @Suppress("TooManyFunctions")
    private class Builder(
        document: ShaderDocument,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        private val caps: RuntimeCapabilities,
    ) {
        private val sizes = hashMapOf(
            ShaderStages.MAIN to (inputWidth to inputHeight),
            ShaderStages.OUTPUT_REF to (outputWidth to outputHeight),
            ShaderStages.NATIVE_CROPPED_REF to (inputWidth to inputHeight),
        )
        private val params = document.params.associate { it.name to it.defaultValue }
        private val customTextures = document.textures.associateBy { it.name }
        private val buffersByName = document.buffers.associateBy { it.name }

        init {
            // Custom textures are sized statically; validation happens at bind time.
            document.textures.forEach { texture ->
                sizes.putIfAbsent(texture.name, texture.width to (texture.height ?: 1))
            }
        }

        private val planned = mutableListOf<PlannedPass>()
        private var linearOpen = false
        private var sigmoidOpen = false
        private var planesOpen = false
        private var planesDirty = false
        private var chromaScaledOpen = false
        private var postStarted = false
        private var offsetX = 0f
        private var offsetY = 0f

        /** The MAIN size at plane-seed time — the size the extraction passes render at. */
        private var planeSeed: Pair<Int, Int>? = null

        fun add(firing: Firing) {
            val slot = ShaderStages.slotOf(firing.hook)
            prepareSlot(slot)
            val pass = planPass(firing.pass, firing.hook, slot) ?: return
            openPlanes(slot)
            openColorStates(slot)
            if (slot == ShaderStages.POST) postStarted = true
            // A plane is dirty once a hooked pass writes a plane in place — only
            // then does the frame need a merge (mpv re-merges planes anyway; we
            // skip the chroma round-trip when nothing touched the planes).
            if (ShaderStages.isPlane(slot) &&
                ShaderStages.isPlane(canonicalStage(firing.pass.save, slot))
            ) {
                planesDirty = true
            }
            planned += pass
            accumulateOffset(pass)
        }

        fun build(skipped: List<String>): GraphPlan {
            closePlanes()
            closeColorStates()
            val presentSlot = if (postStarted) ShaderStages.POST else ShaderStages.MAIN
            val (outW, outH) = sizes.getValue(presentSlot)
            return GraphPlan(planned, outW, outH, skipped, presentSlot, offsetX, offsetY)
        }

        /** Seeds slot sizes and folds finished pipeline phases before a firing plans. */
        private fun prepareSlot(slot: String) {
            when {
                ShaderStages.isPlane(slot) -> seedPlanes(slot)
                slot == ShaderStages.LINEAR -> {
                    closePlanes()
                    sizes[ShaderStages.LINEAR] = sizes.getValue(ShaderStages.MAIN)
                }
                slot == ShaderStages.SIGMOID -> {
                    closePlanes()
                    sizes[ShaderStages.LINEAR] = sizes.getValue(ShaderStages.MAIN)
                    sizes[ShaderStages.SIGMOID] = sizes.getValue(ShaderStages.MAIN)
                }
                slot == ShaderStages.POST -> {
                    closePlanes()
                    closeColorStates()
                    sizes.getOrPut(ShaderStages.POST) { sizes.getValue(ShaderStages.MAIN) }
                }
                else -> {
                    closePlanes()
                    closeColorStates()
                }
            }
        }

        /** Registers the virtual plane sizes: full-res LUMA, half-res CHROMA (4:2:0). */
        private fun seedPlanes(slot: String) {
            val main = sizes.getValue(ShaderStages.MAIN)
            if (planeSeed == null) planeSeed = main
            sizes.getOrPut(ShaderStages.LUMA) { main }
            sizes.getOrPut(ShaderStages.CHROMA) { (main.first + 1) / 2 to (main.second + 1) / 2 }
            if (slot == ShaderStages.CHROMA_SCALED) {
                sizes.getOrPut(ShaderStages.CHROMA_SCALED) { sizes.getValue(ShaderStages.LUMA) }
            }
        }

        /** Inserts the plane-extraction passes before the first pass that hooks a plane. */
        private fun openPlanes(slot: String) {
            if (!ShaderStages.isPlane(slot)) return
            if (!planesOpen) {
                val (w, h) = checkNotNull(planeSeed)
                planned += syntheticPass(
                    "<extract-luma>", listOf(ShaderStages.MAIN), ShaderStages.LUMA,
                    w to h, EXTRACT_LUMA_BODY,
                )
                planned += syntheticPass(
                    "<extract-chroma>", listOf(ShaderStages.MAIN), ShaderStages.CHROMA,
                    (w + 1) / 2 to (h + 1) / 2, EXTRACT_CHROMA_BODY,
                )
                planesOpen = true
            }
            if (slot == ShaderStages.CHROMA_SCALED && !chromaScaledOpen) {
                val (w, h) = sizes.getValue(ShaderStages.CHROMA_SCALED)
                planned += syntheticPass(
                    "<scale-chroma>", listOf(ShaderStages.CHROMA), ShaderStages.CHROMA_SCALED,
                    w to h, SCALE_CHROMA_BODY,
                )
                chromaScaledOpen = true
            }
        }

        /** Merges the (possibly resized) planes back into MAIN at the final LUMA size. */
        private fun closePlanes() {
            if (!planesDirty) return
            val chroma = if (chromaScaledOpen) ShaderStages.CHROMA_SCALED else ShaderStages.CHROMA
            val (w, h) = sizes.getValue(ShaderStages.LUMA)
            planned += syntheticPass(
                "<merge-planes>", listOf(ShaderStages.LUMA, chroma), ShaderStages.MAIN,
                w to h, mergeBody(chroma),
            )
            sizes[ShaderStages.MAIN] = w to h
            planesDirty = false
        }

        /** Inserts linearize/sigmoidize before the first pass that needs the state. */
        private fun openColorStates(slot: String) {
            if ((slot == ShaderStages.LINEAR || slot == ShaderStages.SIGMOID) && !linearOpen) {
                val (w, h) = sizes.getValue(ShaderStages.MAIN)
                planned += syntheticPass(
                    "<linearize>", listOf(ShaderStages.MAIN), ShaderStages.LINEAR,
                    w to h, LINEARIZE_BODY,
                )
                linearOpen = true
            }
            if (slot == ShaderStages.SIGMOID && !sigmoidOpen) {
                val (w, h) = sizes.getValue(ShaderStages.LINEAR)
                planned += syntheticPass(
                    "<sigmoidize>", listOf(ShaderStages.LINEAR), ShaderStages.SIGMOID,
                    w to h, SIGMOIDIZE_BODY,
                )
                sigmoidOpen = true
            }
        }

        /** Converts the frame back to electrical space before MAIN/POST firings and present. */
        private fun closeColorStates() {
            if (sigmoidOpen) {
                val (w, h) = sizes.getValue(ShaderStages.SIGMOID)
                planned += syntheticPass(
                    "<desigmoidize>", listOf(ShaderStages.SIGMOID), ShaderStages.LINEAR,
                    w to h, DESIGMOIDIZE_BODY,
                )
                sigmoidOpen = false
            }
            if (linearOpen) {
                val (w, h) = sizes.getValue(ShaderStages.LINEAR)
                planned += syntheticPass(
                    "<delinearize>", listOf(ShaderStages.LINEAR), ShaderStages.MAIN,
                    w to h, DELINEARIZE_BODY,
                )
                linearOpen = false
            }
        }

        /** A generated runtime pass; the caller manages the [sizes] bookkeeping. */
        private fun syntheticPass(
            desc: String,
            binds: List<String>,
            save: String,
            size: Pair<Int, Int>,
            body: String,
        ): PlannedPass {
            // The caller manages [sizes]: extraction must not clobber a size a
            // shader pass has already evolved (e.g. an in-place LUMA resize).
            val (width, height) = size
            val pass = UserShaderPass(
                desc = desc,
                hooks = listOf(binds.first()),
                binds = binds,
                save = save,
                width = null,
                height = null,
                components = 4,
                condition = null,
                offset = null,
                compute = null,
                body = body,
            )
            return PlannedPass(pass, hook = binds.first(), stage = binds.first(), width, height)
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
                    "pass '${pass.desc}' resizes the non-resizable stage '$slot' " +
                        "(spec: only RGB/LUMA/CHROMA/XYZ/NATIVE/MAIN)",
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
                val buffer = buffersByName[name]
                if (buffer != null) {
                    requireComputeResource(pass, "//!BUFFER '$name'")
                    BufferLayout.sizeOf(buffer) // validates the //!VAR types
                    return@forEach
                }
                // A bound custom texture must be one the executor can create.
                customTextures[name]?.let { validateTextureBind(pass, name, it) }
                // Binding a plane (even from a non-plane pass) forces its extraction.
                if (name in PLANE_SLOTS) {
                    seedPlanes(name)
                    openPlanes(name)
                }
                if (!sizes.containsKey(name)) {
                    throw UserShaderException("pass '${pass.desc}' binds an undefined texture '$name'")
                }
            }
        }

        /** A sampled LUT must be uploadable; a storage image must fit ES 3.1 rules. */
        private fun validateTextureBind(pass: UserShaderPass, name: String, texture: ShaderTexture) {
            ShaderTextureFormats.validate(texture)
            if (!texture.storage) return
            requireComputeResource(pass, "//!TEXTURE STORAGE '$name'")
            val readWrite =
                ShaderBindings.imageAccess(pass.body, name) == ShaderBindings.ImageAccess.READ_WRITE
            if (readWrite && !texture.format.equals("r32f", ignoreCase = true)) {
                throw UserShaderException(
                    "pass '${pass.desc}': ES 3.1 allows read-write images only for r32f, " +
                        "'$name' is '${texture.format}'",
                )
            }
        }

        /** SSBOs and storage images: ES 3.1 only, and only from compute passes. */
        private fun requireComputeResource(pass: UserShaderPass, what: String) {
            if (!caps.ssbo) {
                throw UserShaderException("pass '${pass.desc}' binds $what — needs an ES 3.1 context")
            }
            if (pass.compute == null) {
                throw UserShaderException(
                    "pass '${pass.desc}' binds $what from a fragment pass — " +
                        "ES 3.1 guarantees these units for compute only",
                )
            }
        }

        /** Resolves `MAIN.w`-style size references (raw name first) and bare `//!PARAM` names. */
        private fun valueResolver(pass: UserShaderPass, slot: String): (String) -> Float = { token ->
            val dot = token.indexOf('.')
            if (dot <= 0) {
                params[token] ?: throw UserShaderException("unknown variable '$token' for '${pass.desc}'")
            } else {
                val name = token.substring(0, dot)
                if (name in PLANE_SLOTS) seedPlanes(name)
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

    private val PLANE_SLOTS = setOf(ShaderStages.LUMA, ShaderStages.CHROMA)

    // mpv sigmoid defaults (`sigmoid-center`, `sigmoid-slope`).
    private const val SIGMOID_CENTER = 0.75f
    private const val SIGMOID_SLOPE = 6.5f
    private val SIGMOID_OFFSET = 1f / (1f + exp(SIGMOID_SLOPE * SIGMOID_CENTER))
    private val SIGMOID_SCALE = 1f / (1f + exp(SIGMOID_SLOPE * (SIGMOID_CENTER - 1f))) - SIGMOID_OFFSET

    private const val LINEARIZE_BODY =
        "vec4 hook() { return linearize(clamp(MAIN_tex(MAIN_pos), 0.0, 1.0)); }"

    private const val DELINEARIZE_BODY =
        "vec4 hook() { return delinearize(clamp(LINEAR_tex(LINEAR_pos), 0.0, 1.0)); }"

    // mpv pass_sigmoidize: v = center - log(1/(x*scale + offset) - 1)/slope (x is linear).
    private val SIGMOIDIZE_BODY = """
        vec4 hook() {
          vec4 c = clamp(LINEAR_tex(LINEAR_pos), 0.0, 1.0);
          c.rgb = vec3($SIGMOID_CENTER) -
              log(vec3(1.0) / (c.rgb * $SIGMOID_SCALE + $SIGMOID_OFFSET) - vec3(1.0)) / $SIGMOID_SLOPE;
          return c;
        }
    """.trimIndent()

    // mpv pass_unsigmoidize: x = (1/(1 + exp(slope*(center - v))) - offset)/scale.
    private val DESIGMOIDIZE_BODY = """
        vec4 hook() {
          vec4 c = SIGMOID_tex(SIGMOID_pos);
          c.rgb = (vec3(1.0) / (vec3(1.0) + exp(vec3($SIGMOID_SLOPE) * (vec3($SIGMOID_CENTER) - c.rgb))) -
              $SIGMOID_OFFSET) / $SIGMOID_SCALE;
          return c;
        }
    """.trimIndent()

    // Virtual planes: BT.709 full-range Y'CbCr over the electrical RGB frame.
    // The decompose/merge pair is exactly inverse, so an identity plane chain
    // costs only the chroma subsampling round-trip.
    private const val EXTRACT_LUMA_BODY =
        "vec4 hook() { return vec4(dot(MAIN_tex(MAIN_pos).rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 0.0, 1.0); }"

    private val EXTRACT_CHROMA_BODY = """
        vec4 hook() {
          vec3 c = MAIN_tex(MAIN_pos).rgb;
          float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
          return vec4((c.b - y) / 1.8556 + 0.5, (c.r - y) / 1.5748 + 0.5, 0.0, 1.0);
        }
    """.trimIndent()

    private const val SCALE_CHROMA_BODY =
        "vec4 hook() { return CHROMA_tex(CHROMA_pos); }"

    private fun mergeBody(chroma: String) = """
        vec4 hook() {
          float y = LUMA_tex(LUMA_pos).x;
          vec2 cbcr = ${chroma}_tex(${chroma}_pos).xy - vec2(0.5);
          return vec4(
              y + 1.5748 * cbcr.y,
              y - 0.1873 * cbcr.x - 0.4681 * cbcr.y,
              y + 1.8556 * cbcr.x,
              1.0);
        }
    """.trimIndent()
}
