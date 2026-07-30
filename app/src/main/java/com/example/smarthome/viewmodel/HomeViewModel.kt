package com.example.smarthome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.repository.HomeRepository
import com.example.smarthome.data.repository.MockHomeRepository
import com.example.smarthome.domain.Device
import com.example.smarthome.domain.FloorPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.map

class HomeViewModel(private val repository: HomeRepository = MockHomeRepository()) : ViewModel() {

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

    fun toggleDevice(deviceId: String, isOn: Boolean) {
        viewModelScope.launch {
            repository.toggleDevice(deviceId, isOn)
        }
    }

    fun addFloor(name: String, level: Int) {
        viewModelScope.launch {
            repository.addFloor(name, level)
        }
    }

    fun addRoom(floorId: String, name: String, x: Int, y: Int) {
        viewModelScope.launch {
            repository.addRoom(floorId, name, x, y)
        }
    }
}
