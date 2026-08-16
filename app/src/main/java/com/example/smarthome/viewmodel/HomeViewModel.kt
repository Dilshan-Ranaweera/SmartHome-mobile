package com.example.smarthome.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.repository.FirebaseHomeRepository
import com.example.smarthome.data.repository.HomeRepository
import com.example.smarthome.domain.Device
import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.domain.UsageReport
import com.example.smarthome.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HomeRepository = FirebaseHomeRepository()

    val floorPlans: StateFlow<List<FloorPlan>> = repository.getFloorPlans()
        .map { floors -> floors.sortedByDescending { it.level } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val devices: StateFlow<List<Device>> = repository.getDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val usageReports: StateFlow<List<UsageReport>> = repository.getUsageReports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            while (true) {
                enforceDevicePolicies()
                delay(60_000)
            }
        }
    }

    fun toggleDevice(deviceId: String, isOn: Boolean) {
        viewModelScope.launch {
            repository.toggleDevice(deviceId, isOn)
        }
    }

    fun toggleMultiSwitch(deviceId: String, switchId: String, isOn: Boolean) {
        viewModelScope.launch {
            repository.toggleMultiSwitch(deviceId, switchId, isOn)
        }
    }

    fun addFloor(name: String, level: Int) {
        viewModelScope.launch {
            repository.addFloor(name, level)
        }
    }

    fun addRoom(floorId: String, name: String, x: Int, y: Int, width: Int, height: Int) {
        viewModelScope.launch {
            repository.addRoom(floorId, name, x, y, width, height)
        }
    }

    fun addDeviceToRoom(roomId: String, device: Device) {
        viewModelScope.launch {
            repository.addDeviceToRoom(roomId, device)
        }
    }

    fun updateLightSchedule(deviceId: String, start: String, end: String) {
        viewModelScope.launch {
            repository.updateLightSchedule(deviceId, start, end)
        }
    }

    fun updateSafetyDuration(deviceId: String, maxMinutes: Int) {
        viewModelScope.launch {
            repository.updateSafetyDuration(deviceId, maxMinutes)
        }
    }

    private fun enforceDevicePolicies() {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        
        devices.value.forEach { device ->
            when (device) {
                is Device.SafetyDevice -> {
                    if (device.status == com.example.smarthome.domain.DeviceStatus.ON && device.lastTurnedOnAt != null) {
                        val onDurationMillis = currentTime - device.lastTurnedOnAt
                        if (onDurationMillis > device.maxOnDurationMinutes * 60 * 1000L) {
                            toggleDevice(device.id, false)
                            NotificationHelper.showSafetyNotification(
                                getApplication(),
                                "Safety Alert",
                                "${device.name} turned OFF automatically (Time Limit Exceeded)"
                            )
                        }
                    }
                }
                is Device.Light -> {
                    val start = device.scheduleStart
                    val end = device.scheduleEnd
                    if (!start.isNullOrEmpty() && !end.isNullOrEmpty()) {
                        val startMins = parseTimeStr(start)
                        val endMins = parseTimeStr(end)
                        if (startMins != null && endMins != null) {
                            val isWithinSchedule = if (startMins <= endMins) {
                                currentTimeInMinutes in startMins..endMins
                            } else {
                                // Crosses midnight
                                currentTimeInMinutes >= startMins || currentTimeInMinutes <= endMins
                            }
                            
                            if (isWithinSchedule && device.status == com.example.smarthome.domain.DeviceStatus.OFF) {
                                toggleDevice(device.id, true)
                                NotificationHelper.showSafetyNotification(
                                    getApplication(),
                                    "Light Automated",
                                    "${device.name} turned ON based on schedule"
                                )
                            } else if (!isWithinSchedule && device.status == com.example.smarthome.domain.DeviceStatus.ON) {
                                toggleDevice(device.id, false)
                                NotificationHelper.showSafetyNotification(
                                    getApplication(),
                                    "Light Automated",
                                    "${device.name} turned OFF based on schedule"
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun parseTimeStr(timeStr: String): Int? {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h != null && m != null) {
                return h * 60 + m
            }
        }
        return null
    }
}
