package com.example.smarthome.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarthome.viewmodel.HomeViewModel

@Composable
fun DashboardScreen(viewModel: HomeViewModel) {
    val floors by viewModel.floorPlans.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "My Smart Home", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(floors) { floor ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = floor.name, style = MaterialTheme.typography.titleLarge)
                        Text(text = "${floor.rooms.size} Rooms")
                    }
                }
            }
        }
    }
}

@Composable
fun FloorDetailScreen(floorId: String, viewModel: HomeViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Floor Detail: $floorId")
    }
}

@Composable
fun DeviceDetailScreen(deviceId: String, viewModel: HomeViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Device Control: $deviceId")
    }
}

@Composable
fun ReportsScreen(viewModel: HomeViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Usage Reports & Analytics")
    }
}

@Composable
fun SchedulesScreen(viewModel: HomeViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Schedules & Safety Cutoffs")
    }
}

@Composable
fun CamerasScreen(viewModel: HomeViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Security Camera Monitoring")
    }
}
