package com.rinwave.sakuro.engine.media3.usershader

/** One pass that was actually retained by the runtime planner. */
internal data class RuntimeShaderPass(
    val description: String,
    val outputWidth: Int,
    val outputHeight: Int,
)

/** Configure-time result of the graph that Media3 will execute. */
internal data class ShaderGraphRuntimeStats(
    val outputWidth: Int,
    val outputHeight: Int,
    val passes: List<RuntimeShaderPass>,
    val degradedReason: String? = null,
)
