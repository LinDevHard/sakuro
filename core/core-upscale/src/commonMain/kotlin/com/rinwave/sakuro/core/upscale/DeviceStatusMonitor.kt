package com.rinwave.sakuro.core.upscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Device-status source for [AdaptiveController]:
 * Android listens to PowerManager/BatteryManager; desktop returns static values.
 */
interface DeviceStatusMonitor {
    val status: StateFlow<DeviceStatus>
}

/** A stub for targets without thermals/battery (desktop) and for tests. */
class StaticDeviceStatusMonitor(status: DeviceStatus = DeviceStatus()) : DeviceStatusMonitor {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(status)
}
