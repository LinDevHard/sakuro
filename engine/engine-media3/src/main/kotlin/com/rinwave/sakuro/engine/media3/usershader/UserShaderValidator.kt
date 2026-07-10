package com.rinwave.sakuro.engine.media3.usershader

/**
 * Import-time validation of an mpv user-shader file for the Media3 runtime.
 * Public: the shader-import UI calls this before storing the file so the user
 * sees a reason instead of a silent passthrough at playback time.
 */
object UserShaderValidator {

    private const val PROBE_WIDTH = 1920
    private const val PROBE_HEIGHT = 1080
    private const val PROBE_SCALE = 4

    /**
     * Parses the source and dry-plans it at a nominal size with every
     * capability enabled. Returns null when the shader is usable, otherwise a
     * human-readable problem. A shader whose hooks never fire here (only
     * unsupported planes) is reported too — it would silently do nothing.
     */
    fun validate(source: String): String? {
        val document = try {
            MpvUserShaderParser.parse(source)
        } catch (e: UserShaderException) {
            return e.message
        }
        if (document.passes.isEmpty()) return "no hook passes found — not an mpv user shader?"
        return try {
            val plan = ShaderGraphPlanner.plan(
                document,
                PROBE_WIDTH,
                PROBE_HEIGHT,
                PROBE_WIDTH * PROBE_SCALE,
                PROBE_HEIGHT * PROBE_SCALE,
                RuntimeCapabilities.ES31,
            )
            if (plan.passes.isEmpty() && plan.skipped.isNotEmpty()) {
                "hooks never fire in this pipeline: ${plan.skipped.joinToString()}"
            } else {
                null
            }
        } catch (e: UserShaderException) {
            e.message
        }
    }
}
