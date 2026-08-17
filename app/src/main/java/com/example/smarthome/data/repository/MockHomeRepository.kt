package com.example.smarthome.data.repository

import com.example.smarthome.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

import java.util.UUID

class MockHomeRepository : HomeRepository {
    companion object {
        private val _floorPlans = MutableStateFlow(listOf(
            FloorPlan(
                id = "f1",
                name = "Ground Floor",
                level = 0,
                rooms = listOf(
                    Room("r1", "Living Room", listOf(
                        Device.Outlet("d1", "Main AC Outlet", DeviceStatus.ON),
                        Device.SecurityCamera("c1", "Front Door", DeviceStatus.ON, "mock://stream/front_door")
                    ), x = 0, y = 0, width = 2, height = 2),
                    Room("r2", "Kitchen", listOf(
                        Device.MultiSwitch("d2", "Kitchen Lights", DeviceStatus.OFF, listOf(
                            SwitchUnit("s1", "Main Light", false),
                            SwitchUnit("s2", "Counter Light", false)
                        ))
                    ), x = 2, y = 0, width = 2, height = 2)
                )
            ),
            FloorPlan(
                id = "f2",
                name = "First Floor",
                level = 1,
                rooms = listOf(
                    Room("r3", "Bedroom", listOf(
                        Device.SafetyDevice("d3", "Clothing Iron", DeviceStatus.OFF, maxOnDurationMinutes = 15)
                    ), x = 0, y = 0, width = 2, height = 2)
                )
            )
        ))

        private val _usageReports = MutableStateFlow(listOf(
            UsageReport("1", "Main AC Outlet", "Outlet", 120, 2250.0, System.currentTimeMillis() - 86400000),
            UsageReport("2", "Living Room Light", "Light", 240, 120.0, System.currentTimeMillis() - 86400000),
            UsageReport("3", "Clothing Iron", "SafetyDevice", 15, 450.0, System.currentTimeMillis() - 43200000),
            UsageReport("4", "Kitchen Lights", "MultiSwitch", 180, 180.0, System.currentTimeMillis() - 3600000),
            UsageReport("5", "Main AC Outlet", "Outlet", 60, 750.0, System.currentTimeMillis() - 1800000)
        ))
    }

    override fun getFloorPlans(): Flow<List<FloorPlan>> = _floorPlans.asStateFlow()

    override fun getDevices(): Flow<List<Device>> = _floorPlans.map { floors ->
        floors.flatMap { it.rooms }.flatMap { it.devices }
    }

    override fun getUsageReports(): Flow<List<UsageReport>> = _usageReports.asStateFlow()

    override suspend fun toggleDevice(deviceId: String, isOn: Boolean) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId) {
                            val newStatus = if (isOn) DeviceStatus.ON else DeviceStatus.OFF
                            when (device) {
                                is Device.Outlet -> device.copy(status = newStatus)
                                is Device.MultiSwitch -> {
                                    device.copy(status = newStatus, switches = device.switches.map { it.copy(isOn = isOn) })
                                }
                                is Device.SafetyDevice -> device.copy(
                                    status = newStatus, 
                                    lastTurnedOnAt = if (isOn) System.currentTimeMillis() else device.lastTurnedOnAt,
                                    lastOffReason = if (isOn) null else "manual"
                                )
                                is Device.SecurityCamera -> device.copy(status = newStatus)
                                is Device.Light -> device.copy(
                                    status = newStatus,
                                    lastOffReason = if (isOn) null else "manual"
                                )
                            }
                        } else device
                    })
                })
            }
        }
    }

    override suspend fun toggleMultiSwitch(deviceId: String, switchId: String, isOn: Boolean) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId && device is Device.MultiSwitch) {
                            val updatedSwitches = device.switches.map { 
                                if (it.id == switchId) it.copy(isOn = isOn) else it
                            }
                            val anyOn = updatedSwitches.any { it.isOn }
                            device.copy(
                                switches = updatedSwitches,
                                status = if (anyOn) DeviceStatus.ON else DeviceStatus.OFF
                            )
                        } else device
                    })
                })
            }
        }
    }

    override suspend fun updateLightSchedule(deviceId: String, start: String, end: String) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId && device is Device.Light) {
                            device.copy(scheduleStart = start, scheduleEnd = end)
                        } else device
                    })
                })
            }
        }
    }

    override suspend fun updateSafetyDuration(deviceId: String, maxMinutes: Int) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId && device is Device.SafetyDevice) {
                            device.copy(maxOnDurationMinutes = maxMinutes)
                        } else device
                    })
                })
            }
        }
    }

    override suspend fun addFloor(name: String, level: Int) {
        _floorPlans.update { currentFloors ->
            currentFloors + FloorPlan(
                id = UUID.randomUUID().toString(),
                name = name,
                level = level,
                rooms = emptyList()
            )
        }
    }

    override suspend fun addRoom(floorId: String, name: String, x: Int, y: Int, width: Int, height: Int) {
        _floorPlans.update { currentFloors ->
            currentFloors.map { floor ->
                if (floor.id == floorId) {
                    val newRoom = Room(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        devices = emptyList(),
                        x = x,
                        y = y,
                        width = width,
                        height = height
                    )
                    floor.copy(rooms = floor.rooms + newRoom)
                } else floor
            }
        }
    }

    override suspend fun addDeviceToRoom(roomId: String, device: Device) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    if (room.id == roomId) {
                        room.copy(devices = room.devices + device)
                    } else room
                })
            }
        }
    }

    override suspend fun addUsageReport(report: UsageReport) {
        _usageReports.update { current ->
            current + report
        }
    }

    override suspend fun triggerSafetyCutoff(deviceId: String) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId && device is Device.SafetyDevice) {
                            device.copy(status = DeviceStatus.OFF, lastOffReason = "safety_cutoff")
                        } else device
                    })
                })
            }
        }
    }

    override suspend fun triggerScheduledToggle(deviceId: String, isOn: Boolean) {
        _floorPlans.update { floors ->
            floors.map { floor ->
                floor.copy(rooms = floor.rooms.map { room ->
                    room.copy(devices = room.devices.map { device ->
                        if (device.id == deviceId && device is Device.Light) {
                            device.copy(
                                status = if (isOn) DeviceStatus.ON else DeviceStatus.OFF,
                                lastOffReason = if (isOn) "scheduled_on" else "scheduled_cutoff"
                            )
                        } else device
                    })
                })
            }
        }
    }
}
