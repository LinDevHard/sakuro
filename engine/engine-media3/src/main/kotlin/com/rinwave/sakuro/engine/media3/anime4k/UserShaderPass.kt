package com.rinwave.sakuro.engine.media3.anime4k

/**
 * A single mpv user-shader pass — the `hook()` body plus the parsed directives
 * `//!HOOK/BIND/SAVE/WIDTH/HEIGHT/COMPONENTS/WHEN` (see [MpvUserShaderParser]).
 *
 * The [hook]/[save] names may be pipeline "stages" (`MAIN`, `PREKERNEL`,
 * `HOOKED`) or named intermediates (`conv2d_tf`); the runtime resolves them
 * to concrete textures while executing the graph.
 */
internal data class UserShaderPass(
    val desc: String,
    /** The stage being hooked; `HOOKED` in the body refers to its texture. */
    val hook: String,
    /** Input textures (in `//!BIND` order); may include `HOOKED`/`MAIN`. */
    val binds: List<String>,
    /** Where the result is written; defaults to the hooked stage (in-place). */
    val save: String,
    /** RPN formula for the output width; null → the hooked stage's width. */
    val width: RpnExpression?,
    /** RPN formula for the output height; null → the hooked stage's height. */
    val height: RpnExpression?,
    /** Number of significant output components (1..4); affects semantics only. */
    val components: Int,
    /** RPN condition for applying the pass; null → always apply. */
    val condition: RpnExpression?,
    /** GLSL body: everything between directives (usually `vec4 hook() { ... }`). */
    val body: String,
)
