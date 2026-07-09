package com.rinwave.sakuro.engine.media3.usershader

/**
 * The frame-stage model of the runtime: which mpv hook points exist in our
 * pipeline, in what order they fire, and which physical "frame slot" each one
 * shares.
 *
 * Media3 hands us one RGB frame; mpv's pre-scale stages (`NATIVE`,
 * `MAINPRESUB`, `MAIN`, `PREKERNEL`) are all views of that evolving frame — one
 * [MAIN] slot. `LINEAR`/`SIGMOID` are colorimetric states of the same frame,
 * materialized on demand by synthetic conversion passes, and `LUMA`/`CHROMA`
 * are virtual planes synthesized from (and merged back into) that frame. The
 * post-scale stages (`POSTKERNEL`, `SCALED`, `PREOUTPUT`, `OUTPUT`) share the
 * [POST] slot, which starts as the final `MAIN` (our "scaler kernel" is the
 * identity present pass). `RGB`/`XYZ`/`ALPHA` planes never occur (no RGB/XYZ
 * sources, no alpha in the video path) — passes hooking only them are skipped,
 * exactly as mpv skips hooks on textures that never occur.
 *
 * Known deviation: in mpv `PREKERNEL` sees linearized/sigmoidized data when
 * linear scaling is active; our kernel is the identity, so `PREKERNEL` fires in
 * electrical space (matching mpv's 1:1-scale behavior). Chains hooking both
 * `LINEAR`/`SIGMOID` and `PREKERNEL` will differ.
 */
internal object ShaderStages {

    const val MAIN = "MAIN"
    const val LINEAR = "LINEAR"
    const val SIGMOID = "SIGMOID"

    /**
     * Virtual planes (plan phase 3): synthesized from the RGB frame when a pass
     * hooks them — full-res Y in [LUMA], half-res CbCr in [CHROMA] (emulating
     * 4:2:0 subsampling), chroma at luma resolution in [CHROMA_SCALED]. Merged
     * back into [MAIN] at the final LUMA size before MAIN-family hooks fire.
     */
    const val LUMA = "LUMA"
    const val CHROMA = "CHROMA"
    const val CHROMA_SCALED = "CHROMA_SCALED"

    /** Canonical name of the post-scale frame slot. */
    const val POST = "POSTKERNEL"

    /** RPN pseudo-texture with the target size (`OUTPUT.w`), not a hookable slot key. */
    const val OUTPUT_REF = "OUTPUT"

    /**
     * RPN pseudo-texture with the cropped source size (used by SSimSuperRes and
     * friends). We have no source cropping, so it equals the input size.
     */
    const val NATIVE_CROPPED_REF = "NATIVE_CROPPED"

    /** Hook point → the frame slot it runs on. Also the set of hookable stages. */
    private val SLOT_BY_HOOK = mapOf(
        LUMA to LUMA,
        CHROMA to CHROMA,
        CHROMA_SCALED to CHROMA_SCALED,
        "NATIVE" to MAIN,
        "MAINPRESUB" to MAIN,
        MAIN to MAIN,
        "PREKERNEL" to MAIN,
        LINEAR to LINEAR,
        SIGMOID to SIGMOID,
        "POSTKERNEL" to POST,
        "SCALED" to POST,
        "PREOUTPUT" to POST,
        OUTPUT_REF to POST,
    )

    /** Pipeline firing order of the hook points (mpv `vo=gpu` order). */
    private val FIRING_ORDER = listOf(
        LUMA, CHROMA, CHROMA_SCALED,
        "NATIVE", "MAINPRESUB", MAIN, LINEAR, SIGMOID, "PREKERNEL",
        "POSTKERNEL", "SCALED", "PREOUTPUT", OUTPUT_REF,
    )

    /**
     * Stages whose in-place SAVE may change the size (spec: `RGB LUMA CHROMA
     * XYZ NATIVE MAIN`; `RGB`/`XYZ` never occur in our pipeline).
     */
    private val RESIZABLE = setOf(MAIN, LUMA, CHROMA)

    fun isHookable(hook: String): Boolean = SLOT_BY_HOOK.containsKey(hook)

    fun slotOf(hook: String): String = SLOT_BY_HOOK.getValue(hook)

    /** Canonical slot for a bind/save/RPN name; non-stage names pass through. */
    fun canonicalOrSelf(name: String): String = SLOT_BY_HOOK[name] ?: name

    fun firingOrder(hook: String): Int = FIRING_ORDER.indexOf(hook)

    fun isResizable(slot: String): Boolean = slot in RESIZABLE

    fun isPlane(slot: String): Boolean = slot == LUMA || slot == CHROMA || slot == CHROMA_SCALED
}
