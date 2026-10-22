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
        assertEquals(AdaptiveReason.Thermal(ThermalLevel.MODERATE), decision.reason)
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
        assertEquals(AdaptiveReason.PowerSave, decision.reason)
    }

    @Test
    fun lowBatteryForcesOneStep() {
        val decision = AdaptiveController().update(
            user,
            DeviceStatus(batteryPercent = 10),
            okHealth,
        )
        assertEquals(1, decision.level)
        assertEquals(AdaptiveReason.LowBattery(10), decision.reason)
    }

    @Test
    fun droppedFramesDegradeStepByStep() {
        val controller = AdaptiveController()
        val first = controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f))
        assertEquals(1, first.level)
        assertEquals(AdaptiveReason.FrameDrops(7f), first.reason)
        assertEquals(2, controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f)).level)
    }

    @Test
    fun levelOneDropsHeavyAndDenoiseShadersButKeepsSafeAndImportedEntries() {
        val profile = UpscaleProfile(
            id = "custom",
            name = "Custom",
            passes = listOf(UpscalePass.Upscale(2f)),
            shaderChain = listOf(
                "Anime4K_Restore_CNN_M.glsl",
                "Anime4K_Denoise_Bilateral_Mode.glsl",
                "ravu-r3.hook",
                "imported.glsl",
            ),
            shaderParams = mapOf(
                "Anime4K_Restore_CNN_M.glsl" to mapOf("strength" to 0.8f),
                "ravu-r3.hook" to mapOf("sharpness" to 0.4f),
                "imported.glsl" to mapOf("quality" to 1f),
            ),
        )

        val effective = profile.degradedTo(1)

        assertEquals(listOf("ravu-r3.hook", "imported.glsl"), effective.shaderChain)
        assertEquals(
            setOf("ravu-r3.hook", "imported.glsl"),
            effective.shaderParams.keys,
        )
    }

    @Test
    fun levelTwoDisablesAnExplicitShaderGraphWithoutFallingBackToAbstractPasses() {
        val profile = UpscaleProfile(
            id = "custom",
            name = "Custom",
            passes = listOf(UpscalePass.Upscale(2f), UpscalePass.Sharpen(0.5f)),
            shaderChain = listOf("ravu-r3.hook", "imported.glsl"),
            shaderParams = mapOf("imported.glsl" to mapOf("quality" to 1f)),
        )

        val effective = profile.degradedTo(2)

        assertTrue(effective.shaderChain.isEmpty())
        assertTrue(effective.shaderParams.isEmpty())
        assertTrue(effective.passes.isEmpty())
    }

    @Test
    fun disablingAHeavyOnlyGraphCannotActivateAnAbstractFallback() {
        val profile = UpscaleProfile(
            id = "heavy",
            name = "Heavy",
            passes = listOf(UpscalePass.Upscale(2f)),
            shaderChain = listOf("Anime4K_Upscale_CNN_x2_M.glsl"),
        )

        val effective = profile.degradedTo(1)

        assertTrue(effective.shaderChain.isEmpty())
        assertTrue(effective.passes.isEmpty())
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
            val recovering = controller.update(user, okDevice, okHealth)
            assertEquals(1, recovering.level)
            assertEquals(AdaptiveReason.FrameDrops(7f), recovering.reason)
        }
        assertEquals(0, controller.update(user, okDevice, okHealth).level)
    }

    @Test
    fun activePowerSaveReasonWinsOverHistoricalFrameDropsAtTheSameLevel() {
        val controller = AdaptiveController()
        controller.update(user, okDevice, PlaybackHealth(droppedFramePercent = 7f))

        val decision = controller.update(
            user,
            DeviceStatus(powerSaveMode = true),
            okHealth,
        )

        assertEquals(1, decision.level)
        assertEquals(AdaptiveReason.PowerSave, decision.reason)
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
