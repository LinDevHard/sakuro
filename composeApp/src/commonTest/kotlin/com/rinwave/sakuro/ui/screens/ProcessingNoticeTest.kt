package com.rinwave.sakuro.ui.screens

import com.rinwave.sakuro.core.player.DebugStats
import com.rinwave.sakuro.core.player.UpscaleRuntimeState
import com.rinwave.sakuro.core.upscale.AdaptiveController
import com.rinwave.sakuro.core.upscale.AdaptiveReason
import com.rinwave.sakuro.core.upscale.ThermalLevel
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessingNoticeTest {

    private val requested = UpscaleProfile(
        id = "quality",
        name = "Quality",
        passes = listOf(UpscalePass.Upscale(2f)),
        shaderChain = listOf("ravu-r3.hook"),
    )

    @Test
    fun `adaptive degradation remains visible while processing is reduced`() {
        val notice = processingNotice(
            decision = AdaptiveController.Decision(
                requested = requested,
                effective = requested.copy(shaderChain = listOf("CAS.glsl")),
                level = 1,
                reason = AdaptiveReason.Thermal(ThermalLevel.MODERATE),
            ),
            stats = DebugStats(upscaleRuntimeState = UpscaleRuntimeState.APPLIED),
        )

        val adaptive = assertIs<ProcessingNotice.Adaptive>(notice)
        assertEquals(1, adaptive.level)
        assertEquals(AdaptiveReason.Thermal(ThermalLevel.MODERATE), adaptive.reason)
        assertEquals(ProcessingOutcome.REDUCED, adaptive.outcome)
    }

    @Test
    fun `adaptive notice says disabled when no processing remains`() {
        val notice = processingNotice(
            decision = AdaptiveController.Decision(
                requested = requested,
                effective = requested.copy(passes = emptyList(), shaderChain = emptyList()),
                level = 2,
                reason = AdaptiveReason.FrameDrops(14f),
            ),
            stats = DebugStats(upscaleRuntimeState = UpscaleRuntimeState.INACTIVE),
        )

        val adaptive = assertIs<ProcessingNotice.Adaptive>(notice)
        assertEquals(ProcessingOutcome.DISABLED, adaptive.outcome)
    }

    @Test
    fun `runtime passthrough takes priority and exposes the engine reason`() {
        val notice = processingNotice(
            decision = AdaptiveController.Decision(
                requested = requested,
                effective = requested,
                level = 0,
            ),
            stats = DebugStats(
                upscaleRuntimeState = UpscaleRuntimeState.PASSTHROUGH,
                upscaleRuntimeReason = "FP16 unavailable",
            ),
        )

        val unavailable = assertIs<ProcessingNotice.RuntimeFallback>(notice)
        assertEquals(UpscaleRuntimeState.PASSTHROUGH, unavailable.state)
        assertEquals("FP16 unavailable", unavailable.reason)
    }

    @Test
    fun `pending shader graph is visible until configuration finishes`() {
        val notice = processingNotice(
            decision = AdaptiveController.Decision(
                requested = requested,
                effective = requested,
                level = 0,
            ),
            stats = DebugStats(upscaleRuntimeState = UpscaleRuntimeState.PENDING),
        )

        assertIs<ProcessingNotice.RuntimePending>(notice)
    }

    @Test
    fun `healthy applied processing has no notice`() {
        val notice = processingNotice(
            decision = AdaptiveController.Decision(
                requested = requested,
                effective = requested,
                level = 0,
            ),
            stats = DebugStats(upscaleRuntimeState = UpscaleRuntimeState.APPLIED),
        )

        assertNull(notice)
    }

    @Test
    fun `reporter logs only transitions and includes requested and effective chains`() {
        val messages = mutableListOf<String>()
        val reporter = ProcessingStatusReporter(messages::add)
        val decision = AdaptiveController.Decision(
            requested = requested,
            effective = requested.copy(passes = emptyList(), shaderChain = emptyList()),
            level = 2,
            reason = AdaptiveReason.FrameDrops(14f),
        )
        val stats = DebugStats(upscaleRuntimeState = UpscaleRuntimeState.INACTIVE)

        reporter.report(decision, stats)
        reporter.report(decision, stats)

        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("L2"))
        assertTrue(messages.single().contains("frame drops 14.0%"))
        assertTrue(messages.single().contains("requested=Quality[ravu-r3.hook]"))
        assertTrue(messages.single().contains("effective=Quality[off]"))
    }

    @Test
    fun `reporter records recovery after a degraded state`() {
        val messages = mutableListOf<String>()
        val reporter = ProcessingStatusReporter(messages::add)
        reporter.report(
            AdaptiveController.Decision(
                requested = requested,
                effective = requested.copy(passes = emptyList(), shaderChain = emptyList()),
                level = 2,
                reason = AdaptiveReason.PowerSave,
            ),
            DebugStats(upscaleRuntimeState = UpscaleRuntimeState.INACTIVE),
        )

        reporter.report(
            AdaptiveController.Decision(requested = requested, effective = requested, level = 0),
            DebugStats(upscaleRuntimeState = UpscaleRuntimeState.APPLIED),
        )

        assertEquals(2, messages.size)
        assertTrue(messages.last().contains("recovered"))
    }

    @Test
    fun `runtime driver reason is normalized to one log line`() {
        val messages = mutableListOf<String>()
        val reporter = ProcessingStatusReporter(messages::add)

        reporter.report(
            AdaptiveController.Decision(requested = requested, effective = requested, level = 0),
            DebugStats(
                upscaleRuntimeState = UpscaleRuntimeState.PASSTHROUGH,
                upscaleRuntimeReason = "shader link failed:\nversion mismatch",
            ),
        )

        assertEquals(1, messages.size)
        assertTrue('\n' !in messages.single())
        assertTrue(messages.single().contains("shader link failed: version mismatch"))
    }
}
