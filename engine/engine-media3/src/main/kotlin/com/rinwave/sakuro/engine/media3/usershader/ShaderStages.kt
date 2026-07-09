package com.rinwave.sakuro.engine.media3.usershader

/**
 * The frame-stage model of the runtime: which mpv hook points exist in our
 * pipeline, in what order they fire, and which physical "frame slot" each one
 * shares.
 *
 * Media3 hands us one RGB frame; mpv's pre-scale stages (`NATIVE`,
 * `MAINPRESUB`, `MAIN`, `PREKERNEL`) are all views of that evolving frame — one
 * [MAIN] slot. `LINEAR`/`SIGMOID` are colorimetric states of the same frame,
 * materialized on demand by synthetic conversion passes. The post-scale stages
 * (`POSTKERNEL`, `SCALED`, `PREOUTPUT`, `OUTPUT`) share the [POST] slot, which
 * starts as the final `MAIN` (our "scaler kernel" is the identity present
 * pass). The plane stages (`LUMA`/`CHROMA`/`RGB`/`ALPHA`/`XYZ`) arrive with the
 * virtual-plane emulation (plan phase 3) — until then passes hooking only them
 * are skipped, exactly as mpv skips hooks on textures that never occur.
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
        "NATIVE", "MAINPRESUB", MAIN, LINEAR, SIGMOID, "PREKERNEL",
        "POSTKERNEL", "SCALED", "PREOUTPUT", OUTPUT_REF,
    )

    /**
     * Stages whose in-place SAVE may change the size (spec: `RGB LUMA CHROMA
     * XYZ NATIVE MAIN`; of those only the MAIN family exists before phase 3).
     */
    private val RESIZABLE = setOf(MAIN)

    fun isHookable(hook: String): Boolean = SLOT_BY_HOOK.containsKey(hook)

    fun slotOf(hook: String): String = SLOT_BY_HOOK.getValue(hook)

    /** Canonical slot for a bind/save/RPN name; non-stage names pass through. */
    fun canonicalOrSelf(name: String): String = SLOT_BY_HOOK[name] ?: name

    fun firingOrder(hook: String): Int = FIRING_ORDER.indexOf(hook)

    fun isResizable(slot: String): Boolean = slot in RESIZABLE
}
