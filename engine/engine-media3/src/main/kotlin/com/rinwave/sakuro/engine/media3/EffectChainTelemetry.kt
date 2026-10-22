package com.rinwave.sakuro.engine.media3

import com.rinwave.sakuro.core.player.UpscaleRuntimeState
import com.rinwave.sakuro.engine.media3.usershader.ShaderGraphRuntimeStats
import kotlin.math.roundToInt

/** Honest runtime view of the Media3 effects that have actually configured. */
internal data class EffectChainStats(
    val outputWidth: Int? = null,
    val outputHeight: Int? = null,
    val effects: List<String> = emptyList(),
    val pending: Boolean = false,
    val degradedReasons: List<String> = emptyList(),
    val runtimeState: UpscaleRuntimeState = UpscaleRuntimeState.INACTIVE,
)

/**
 * Collects configure-time shader plans from Media3's GL thread. A chain is
 * reported only after every shader effect has configured; until then callers
 * get [EffectChainStats.pending] instead of a profile-derived guess.
 */
internal class EffectChainTelemetry {

    private data class Accumulator(
        var width: Int,
        var height: Int,
        val effects: MutableList<String> = mutableListOf(),
        val degradedReasons: MutableList<String> = mutableListOf(),
    )

    private sealed interface Node {
        data class Shader(
            val label: String,
            var runtime: ShaderGraphRuntimeStats? = null,
        ) : Node

        data class Presentation(val targetHeight: Int) : Node
    }

    private val nodes = mutableListOf<Node>()
    private var chainFailure: String? = null

    @Synchronized
    fun addShader(label: String): (ShaderGraphRuntimeStats) -> Unit {
        val node = Node.Shader(label)
        nodes += node
        return { runtime -> record(node, runtime) }
    }

    @Synchronized
    fun addPresentation(targetHeight: Int) {
        nodes += Node.Presentation(targetHeight)
    }

    @Synchronized
    fun fail(reason: String) {
        chainFailure = reason
    }

    @Synchronized
    fun snapshot(sourceWidth: Int, sourceHeight: Int): EffectChainStats {
        chainFailure?.let { reason ->
            return EffectChainStats(
                outputWidth = sourceWidth.takeIf { it > 0 },
                outputHeight = sourceHeight.takeIf { it > 0 },
                effects = listOf("effect chain: passthrough"),
                degradedReasons = listOf(reason),
                runtimeState = UpscaleRuntimeState.PASSTHROUGH,
            )
        }
        if (nodes.any { it is Node.Shader && it.runtime == null }) {
            return EffectChainStats(pending = true, runtimeState = UpscaleRuntimeState.PENDING)
        }

        val result = Accumulator(sourceWidth, sourceHeight)
        nodes.forEach { applyNode(result, it) }
        return EffectChainStats(
            outputWidth = result.width.takeIf { it > 0 },
            outputHeight = result.height.takeIf { it > 0 },
            effects = result.effects,
            degradedReasons = result.degradedReasons,
            runtimeState = when {
                nodes.isEmpty() -> UpscaleRuntimeState.INACTIVE
                result.degradedReasons.isEmpty() -> UpscaleRuntimeState.APPLIED
                result.effects.all { it.endsWith(": passthrough") } -> UpscaleRuntimeState.PASSTHROUGH
                else -> UpscaleRuntimeState.DEGRADED
            },
        )
    }

    private fun applyNode(result: Accumulator, node: Node) {
        when (node) {
            is Node.Shader -> applyShader(result, node)
            is Node.Presentation -> applyPresentation(result, node)
        }
    }

    private fun applyShader(result: Accumulator, node: Node.Shader) {
        val runtime = checkNotNull(node.runtime)
        result.width = runtime.outputWidth
        result.height = runtime.outputHeight
        if (runtime.degradedReason != null) {
            result.effects += "${node.label}: passthrough"
            result.degradedReasons += "${node.label}: ${runtime.degradedReason}"
            return
        }
        runtime.passes.forEach { pass ->
            result.effects += "${node.label}: ${pass.description} ${pass.outputWidth}x${pass.outputHeight}"
        }
    }

    private fun applyPresentation(result: Accumulator, node: Node.Presentation) {
        if (result.width <= 0 || result.height <= 0) return
        result.width = (result.width.toDouble() * node.targetHeight / result.height).roundToInt()
        result.height = node.targetHeight
        result.effects += "Presentation: ${result.width}x${result.height}"
    }

    @Synchronized
    private fun record(node: Node.Shader, runtime: ShaderGraphRuntimeStats) {
        node.runtime = runtime
    }
}
