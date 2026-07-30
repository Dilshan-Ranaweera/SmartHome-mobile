package com.example.smarthome.data.repository

import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.domain.Device
import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    fun getFloorPlans(): Flow<List<FloorPlan>>
    fun getDevices(): Flow<List<Device>>
    suspend fun toggleDevice(deviceId: String, isOn: Boolean)
    suspend fun addFloor(name: String, level: Int)
}
