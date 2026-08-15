package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.settings.SakuroSettings
import kotlinx.coroutines.flow.StateFlow

/** Advanced tab: adaptive shader switching, the debug overlay and the benchmark. */
class AdvancedSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
    val onOpenBench: () -> Unit,
) : ComponentContext by componentContext {

    val adaptiveEnabled: StateFlow<Boolean> = settings.adaptiveEnabled
    val debugOverlay: StateFlow<Boolean> = settings.debugOverlay

    fun setAdaptiveEnabled(enabled: Boolean) = settings.setAdaptiveEnabled(enabled)

    fun setDebugOverlay(enabled: Boolean) = settings.setDebugOverlay(enabled)
}
