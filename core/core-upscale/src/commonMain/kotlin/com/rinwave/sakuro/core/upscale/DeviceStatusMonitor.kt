package com.rinwave.sakuro.core.upscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Источник состояния устройства для [AdaptiveController]:
 * Android слушает PowerManager/BatteryManager, desktop отдаёт статику.
 */
interface DeviceStatusMonitor {
    val status: StateFlow<DeviceStatus>
}

/** Заглушка для таргетов без термала/батареи (desktop) и для тестов. */
class StaticDeviceStatusMonitor(status: DeviceStatus = DeviceStatus()) : DeviceStatusMonitor {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(status)
}
