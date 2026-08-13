package com.example.smarthome.data.repository

import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.domain.Device
import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun getFloorPlans(): Flow<List<FloorPlan>>
    fun getDevices(): Flow<List<Device>>
    suspend fun toggleDevice(deviceId: String, isOn: Boolean)
    suspend fun toggleMultiSwitch(deviceId: String, switchId: String, isOn: Boolean)
    suspend fun addFloor(name: String, level: Int)
    suspend fun addRoom(floorId: String, name: String, x: Int, y: Int, width: Int, height: Int)
    suspend fun addDeviceToRoom(roomId: String, device: Device)
}
