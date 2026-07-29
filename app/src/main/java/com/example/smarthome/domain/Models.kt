package com.example.smarthome.domain

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceStatus {
    ON, OFF, ERROR, DISCONNECTED
}

@Serializable
sealed class Device {
    abstract val id: String
    abstract val name: String
    abstract val status: DeviceStatus

    @Serializable
    data class Outlet(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus = DeviceStatus.OFF
    ) : Device()

    @Serializable
    data class MultiSwitch(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus = DeviceStatus.OFF,
        val switches: List<SwitchUnit>
    ) : Device()

    @Serializable
    data class SafetyDevice(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus = DeviceStatus.OFF,
        val maxOnDurationMinutes: Int,
        val lastTurnedOnAt: Long? = null
    ) : Device()

    @Serializable
    data class SecurityCamera(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus = DeviceStatus.DISCONNECTED,
        val streamUri: String? = null
    ) : Device()
}

@Serializable
data class SwitchUnit(
    val id: String,
    val name: String,
    val isOn: Boolean
)

@Serializable
data class Room(
    val id: String,
    val name: String,
    val devices: List<Device>
)

@Serializable
data class FloorPlan(
    val id: String,
    val name: String,
    val rooms: List<Room>
)
