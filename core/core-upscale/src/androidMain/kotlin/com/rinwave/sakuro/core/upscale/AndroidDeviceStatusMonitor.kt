package com.rinwave.sakuro.core.upscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-источник для адаптивного контроллера (ARCHITECTURE.md §5):
 * тепловой статус (API 29+), режим энергосбережения и уровень заряда.
 * Живёт весь срок процесса — слушатели не снимаются.
 */
class AndroidDeviceStatusMonitor(context: Context) : DeviceStatusMonitor {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private var thermal = ThermalLevel.NONE

    private val _status = MutableStateFlow(DeviceStatus())
    override val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermal = mapThermal(powerManager.currentThermalStatus)
            powerManager.addThermalStatusListener { status ->
                thermal = mapThermal(status)
                refresh()
            }
        }
        val filter = IntentFilter().apply {
            // Оба — защищённые системные броадкасты, флаг exported не требуется.
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        appContext.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = refresh()
            },
            filter,
        )
        refresh()
    }

    private fun refresh() {
        val battery = batteryManager
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 1..100 }
        _status.value = DeviceStatus(
            thermal = thermal,
            powerSaveMode = powerManager.isPowerSaveMode,
            batteryPercent = battery,
        )
    }

    private fun mapThermal(status: Int): ThermalLevel = when {
        status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        status == PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        status == PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        status == PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        else -> ThermalLevel.NONE
    }
}
