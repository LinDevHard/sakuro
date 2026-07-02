package com.rinwave.sakuro.core.upscale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveControllerTest {

    private val user = BuiltInPresets.ANIME_SD
    private val okDevice = DeviceStatus()
    private val okHealth = PlaybackHealth()

    @Test
    fun fullPresetWhenEverythingHealthy() {
        val decision = AdaptiveController().update(user, okDevice, okHealth)
        assertEquals(0, decision.level)
        assertEquals(user, decision.effective)
        assertNull(decision.reason)
    }

    @Test
    fun criticalThermalDisablesProcessing() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(thermal = ThermalLevel.CRITICAL),
            okHealth,
        )
        assertEquals(AdaptiveController.MAX_LEVEL, decision.level)
        assertTrue(decision.effective.passes.isEmpty())
        assertNotNull(decision.reason)
    }

    @Test
    fun moderateThermalDropsDenoise() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(thermal = ThermalLevel.MODERATE),
            okHealth,
        )
        assertEquals(1, decision.level)
        assertTrue(decision.effective.passes.none { it is UpscalePass.Denoise })
        assertTrue(decision.effective.passes.any { it is UpscalePass.Sharpen })
    }

    @Test
    fun severeThermalCapsUpscaleFactor() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(thermal = ThermalLevel.SEVERE),
            okHealth,
        )
        assertEquals(2, decision.level)
        val upscale = decision.effective.passes.filterIsInstance<UpscalePass.Upscale>().single()
        assertTrue(upscale.factor <= 1.5f)
        assertTrue(decision.effective.passes.none { it is UpscalePass.Sharpen })
    }

    @Test
    fun powerSaveModeForcesOneStep() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(powerSaveMode = true),
            okHealth,
        )
        assertEquals(1, decision.level)
    }

    @Test
    fun lowBatteryForcesOneStep() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(batteryPercent = 10),
            okHealth,
        )
        assertEquals(1, decision.level)
    }

    @Test
    fun droppedFramesDegradeStepByStep() {
        val controller = AdaptiveController()
        assertEquals(1, controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f)).level)
        assertEquals(2, controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f)).level)
    }

    @Test
    fun severeDropsDegradeTwoSteps() {
        val controller = AdaptiveController()
        val decision = controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 20f))
        assertEquals(2, decision.level)
    }

    @Test
    fun recoversAfterHealthyStreak() {
        val controller = AdaptiveController(AdaptiveController.Config(recoverySamples = 3))
        controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f))
        repeat(2) {
            assertEquals(1, controller.update(user, okDevice, okHealth).level)
        }
        assertEquals(0, controller.update(user, okDevice, okHealth).level)
    }

    @Test
    fun degradationNeverExceedsMax() {
        val controller = AdaptiveController()
        repeat(5) {
            controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 50f))
        }
        val decision = controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 50f))
        assertEquals(AdaptiveController.MAX_LEVEL, decision.level)
        assertTrue(decision.effective.passes.isEmpty())
    }

    @Test
    fun offPresetPassesThroughUnchanged() {
        val decision = AdaptiveController().update(
            BuiltInPresets.OFF,
            DeviceStatus(thermal = ThermalLevel.SEVERE),
            okHealth,
        )
        assertEquals(BuiltInPresets.OFF, decision.effective)
    }

    @Test
    fun resetClearsAccumulatedDegradation() {
        val controller = AdaptiveController()
        controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 20f))
        controller.reset()
        assertEquals(0, controller.update(user, okDevice, okHealth).level)
    }
}
