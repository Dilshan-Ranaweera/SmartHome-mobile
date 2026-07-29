package com.example.smarthome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarthome.domain.Device
import com.example.smarthome.domain.DeviceStatus
import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.viewmodel.HomeViewModel

@Composable
fun DashboardScreen(viewModel: HomeViewModel, onFloorClick: (String) -> Unit) {
    val floors by viewModel.floorPlans.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "My Smart Home", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(floors) { floor ->
                FloorSummaryCard(floor = floor, onClick = { onFloorClick(floor.id) })
            }
        }
    }
}

@Composable
fun FloorSummaryCard(floor: FloorPlan, onClick: () -> Unit) {
    val totalDevices = floor.rooms.flatMap { it.devices }.size
    val activeDevices = floor.rooms.flatMap { it.devices }.count { it.status == DeviceStatus.ON }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.0f)) {
                Text(text = floor.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${floor.rooms.size} Rooms • $totalDevices Devices",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (activeDevices > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(text = "$activeDevices ON", modifier = Modifier.padding(4.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorDetailScreen(
    floorId: String,
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    val floors by viewModel.floorPlans.collectAsState()
    val floor = floors.find { it.id == floorId }

    if (floor == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Floor not found")
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(floor.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(text = "Rooms", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                // Abstract grid representation of rooms
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(floor.rooms) { room ->
                        RoomCard(room.name, room.devices.size) { }
                    }
                }
            }

            item {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            floor.rooms.forEach { room ->
                item {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(room.devices) { device ->
                    DeviceItem(
                        device = device,
                        onToggle = { isOn -> viewModel.toggleDevice(device.id, isOn) },
                        onClick = { onDeviceClick(device.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(device: Device, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (device.status == DeviceStatus.ON) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = { Text(device.status.name) },
            leadingContent = {
                val icon = when (device) {
                    is Device.Outlet -> Icons.Default.Power
                    is Device.MultiSwitch -> Icons.Default.SettingsInputComponent
                    is Device.SafetyDevice -> Icons.Default.Warning
                    is Device.SecurityCamera -> Icons.Default.Videocam
                }
                Icon(icon, contentDescription = null)
            },
            trailingContent = {
                if (device !is Device.SecurityCamera) {
                    Switch(
                        checked = device.status == DeviceStatus.ON,
                        onCheckedChange = onToggle
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
fun RoomCard(name: String, deviceCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(text = "$deviceCount Devices", style = MaterialTheme.typography.bodySmall)
        }
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
