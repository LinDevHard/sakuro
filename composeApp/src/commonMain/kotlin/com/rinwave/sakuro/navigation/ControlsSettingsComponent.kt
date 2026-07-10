package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.settings.SakuroSettings
import kotlinx.coroutines.flow.StateFlow

/** Controls tab: player gestures (master + per-gesture switches) and their sensitivity. */
class ControlsSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SakuroSettings,
) : ComponentContext by componentContext {

    val gesturesEnabled: StateFlow<Boolean> = settings.gesturesEnabled
    val gestureBrightness: StateFlow<Boolean> = settings.gestureBrightness
    val gestureVolume: StateFlow<Boolean> = settings.gestureVolume
    val gestureSeek: StateFlow<Boolean> = settings.gestureSeek
    val gestureZoom: StateFlow<Boolean> = settings.gestureZoom
    val gestureSensitivity: StateFlow<Float> = settings.gestureSensitivity

    fun setGesturesEnabled(enabled: Boolean) = settings.setGesturesEnabled(enabled)

    fun setGestureBrightness(enabled: Boolean) = settings.setGestureBrightness(enabled)

    fun setGestureVolume(enabled: Boolean) = settings.setGestureVolume(enabled)

    fun setGestureSeek(enabled: Boolean) = settings.setGestureSeek(enabled)

    fun setGestureZoom(enabled: Boolean) = settings.setGestureZoom(enabled)

    fun setGestureSensitivity(value: Float) = settings.setGestureSensitivity(value)
}
