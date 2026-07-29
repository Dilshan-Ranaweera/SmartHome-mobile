package com.example.smarthome.data.repository

import com.example.smarthome.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MockHomeRepository : HomeRepository {
    private val _floorPlans = MutableStateFlow(listOf(
        FloorPlan(
            id = "f1",
            name = "Ground Floor",
            rooms = listOf(
                Room("r1", "Living Room", listOf(
                    Device.Outlet("d1", "Main AC Outlet", DeviceStatus.ON),
                    Device.SecurityCamera("c1", "Front Door", DeviceStatus.ON, "mock://stream/front_door")
                )),
                Room("r2", "Kitchen", listOf(
                    Device.MultiSwitch("d2", "Kitchen Lights", DeviceStatus.OFF, listOf(
                        SwitchUnit("s1", "Main Light", false),
                        SwitchUnit("s2", "Counter Light", false)
                    ))
                ))
            )
        ),
        FloorPlan(
            id = "f2",
            name = "First Floor",
            rooms = listOf(
                Room("r3", "Bedroom", listOf(
                    Device.SafetyDevice("d3", "Clothing Iron", DeviceStatus.OFF, maxOnDurationMinutes = 15)
                ))
            )
        )
    ))

    override fun getFloorPlans(): Flow<List<FloorPlan>> = _floorPlans.asStateFlow()

    override fun getDevices(): Flow<List<Device>> = _floorPlans.map { floors ->
        floors.flatMap { it.rooms }.flatMap { it.devices }
    }

    override suspend fun toggleDevice(deviceId: String, isOn: Boolean) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId) {
                            val newStatus = if (isOn) DeviceStatus.ON else DeviceStatus.OFF
                            when (device) {
                                is Device.Outlet -> device.copy(status = newStatus)
                                is Device.MultiSwitch -> device.copy(status = newStatus)
                                is Device.SafetyDevice -> device.copy(status = newStatus)
                                is Device.SecurityCamera -> device.copy(status = newStatus)
                            }
                        } else device
                    })
                })
            }
        }
    }
}
