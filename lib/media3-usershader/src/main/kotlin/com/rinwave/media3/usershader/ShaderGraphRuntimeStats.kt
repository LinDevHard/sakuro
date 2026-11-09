package com.rinwave.media3.usershader

/** One pass that was actually retained by the runtime planner. */
public data class RuntimeShaderPass(
    public val description: String,
    public val outputWidth: Int,
    public val outputHeight: Int,
)

/** Configure-time result of the graph that Media3 will execute. */
public data class ShaderGraphRuntimeStats(
    public val outputWidth: Int,
    public val outputHeight: Int,
    public val passes: List<RuntimeShaderPass>,
    public val degradedReason: String? = null,
)
