package com.rinwave.sakuro.ui.screens

import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.UpscaleRuntimeState
import com.rinwave.sakuro.core.upscale.AdaptiveController
import com.rinwave.sakuro.core.upscale.AdaptiveReason
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import com.rinwave.sakuro.core.upscale.describe

enum class ProcessingOutcome { REDUCED, DISABLED }

sealed interface ProcessingNotice {
    data class Adaptive(
        val level: Int,
        val reason: AdaptiveReason,
        val outcome: ProcessingOutcome,
    ) : ProcessingNotice

    data object RuntimePending : ProcessingNotice

    data class RuntimeFallback(
        val state: UpscaleRuntimeState,
        val reason: String?,
    ) : ProcessingNotice
}

fun processingNotice(
    decision: AdaptiveController.Decision?,
    stats: DebugStats,
): ProcessingNotice? {
    val adaptiveReason = decision?.reason
    return when {
        stats.upscaleRuntimeState == UpscaleRuntimeState.PASSTHROUGH ||
            stats.upscaleRuntimeState == UpscaleRuntimeState.DEGRADED -> {
            ProcessingNotice.RuntimeFallback(stats.upscaleRuntimeState, stats.upscaleRuntimeReason)
        }
        decision != null && decision.level > 0 && adaptiveReason != null -> ProcessingNotice.Adaptive(
            level = decision.level,
            reason = adaptiveReason,
            outcome = if (decision.effective.isEnabled) ProcessingOutcome.REDUCED else ProcessingOutcome.DISABLED,
        )
        stats.upscaleRuntimeState == UpscaleRuntimeState.PENDING -> ProcessingNotice.RuntimePending
        else -> null
    }
}

/** Emits one technical log line per visible status transition, never per stats sample. */
class ProcessingStatusReporter(private val log: (String) -> Unit) {
    private var lastSnapshot: Snapshot? = null

    fun report(decision: AdaptiveController.Decision, stats: DebugStats) {
        val snapshot = Snapshot(
            notice = processingNotice(decision, stats),
            requested = decision.requested.summary(),
            effective = decision.effective.summary(),
            runtimeState = stats.upscaleRuntimeState,
        )
        if (snapshot == lastSnapshot) return

        val previous = lastSnapshot
        lastSnapshot = snapshot
        when {
            snapshot.notice != null -> log(snapshot.message(decision))
            previous?.notice != null -> log(
                "processing recovered; requested=${snapshot.requested}; effective=${snapshot.effective}",
            )
        }
    }

    private data class Snapshot(
        val notice: ProcessingNotice?,
        val requested: String,
        val effective: String,
        val runtimeState: UpscaleRuntimeState,
    ) {
        fun message(decision: AdaptiveController.Decision): String = when (val current = notice) {
            is ProcessingNotice.Adaptive -> "processing L${current.level} ${current.reason.technicalText()}; " +
                "outcome=${current.outcome.name.lowercase()}; runtime=$runtimeState; " +
                "requested=$requested; effective=$effective"
            is ProcessingNotice.RuntimeFallback -> "processing runtime ${runtimeState.name.lowercase()}: " +
                "${current.reason?.singleLine() ?: "unknown reason"}; " +
                "requested=$requested; effective=$effective"
            ProcessingNotice.RuntimePending -> "processing runtime pending; requested=$requested; effective=$effective"
            null -> "processing L${decision.level}"
        }
    }
}

private fun String.singleLine(): String = trim().replace(Regex("\\s+"), " ")

internal fun AdaptiveReason.technicalText(): String = when (this) {
    is AdaptiveReason.Thermal -> "thermal ${level.name.lowercase()}"
    is AdaptiveReason.FrameDrops -> "frame drops $percent%"
    AdaptiveReason.PowerSave -> "power save"
    is AdaptiveReason.LowBattery -> "battery $percent%"
}

private fun UpscaleProfile.summary(): String {
    val chain = when {
        shaderChain.isNotEmpty() -> shaderChain.joinToString(" → ")
        passes.isNotEmpty() -> passes.joinToString(" → ") { it.describe() }
        else -> "off"
    }
    return "$name[$chain]"
}
