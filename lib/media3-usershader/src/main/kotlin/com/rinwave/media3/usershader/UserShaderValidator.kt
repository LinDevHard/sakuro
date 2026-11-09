package com.rinwave.media3.usershader

/** Outcome of checking a shader against the runtime and, optionally, a device. */
public sealed interface ShaderCheck {
    /** Runs on this device (or, without a device, on a full ES 3.1 GPU). */
    public data object Ok : ShaderCheck

    /** Broken everywhere: not an mpv user shader, or a graph the runtime cannot plan. */
    public data class Invalid(public val reason: String) : ShaderCheck

    /** A valid shader this device cannot run (needs ES 3.1, or FP16 render targets). */
    public data class Unsupported(public val reason: String) : ShaderCheck
}

/**
 * Import-time validation of an mpv user-shader file for the Media3 runtime.
 * Public: the shader-import UI calls this before storing the file so the user
 * sees a reason instead of a silent passthrough at playback time.
 */
public object UserShaderValidator {

    private const val PROBE_WIDTH = 1920
    private const val PROBE_HEIGHT = 1080
    private const val PROBE_SCALE = 4

    /**
     * Parses the source and dry-plans it at a nominal size with every
     * capability enabled. Returns null when the shader is usable, otherwise a
     * human-readable problem. A shader whose hooks never fire here (only
     * unsupported planes) is reported too — it would silently do nothing.
     */
    public fun validate(source: String): String? = (check(source) as? ShaderCheck.Invalid)?.reason

    /**
     * Like [validate], and additionally decides whether [gpu] can run the
     * shader. A null [gpu] (unknown device) only checks validity.
     */
    public fun check(source: String, gpu: GpuCapabilities? = null): ShaderCheck {
        val document = try {
            MpvUserShaderParser.parse(source)
        } catch (e: UserShaderException) {
            return ShaderCheck.Invalid(e.message.orEmpty())
        }
        return check(document, gpu)
    }

    /** [check] for an already parsed document (several files merged count as one chain). */
    public fun check(document: ShaderDocument, gpu: GpuCapabilities? = null): ShaderCheck {
        if (document.passes.isEmpty()) return ShaderCheck.Invalid("no hook passes found — not an mpv user shader?")
        planProblem(document, RuntimeCapabilities.ES31)?.let { return ShaderCheck.Invalid(it) }
        if (gpu == null) return ShaderCheck.Ok
        if (!gpu.fp16Renderable) {
            return ShaderCheck.Unsupported("this GPU cannot render to FP16 textures")
        }
        val problem = planProblem(document, gpu.runtimeCapabilities()) ?: return ShaderCheck.Ok
        return ShaderCheck.Unsupported(problem)
    }

    private fun planProblem(document: ShaderDocument, caps: RuntimeCapabilities): String? = try {
        val plan = ShaderGraphPlanner.plan(
            document,
            PROBE_WIDTH,
            PROBE_HEIGHT,
            PROBE_WIDTH * PROBE_SCALE,
            PROBE_HEIGHT * PROBE_SCALE,
            caps,
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
