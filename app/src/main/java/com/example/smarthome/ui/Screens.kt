package com.example.smarthome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarthome.domain.Device
import com.example.smarthome.domain.DeviceStatus
import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.viewmodel.HomeViewModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: HomeViewModel, onFloorClick: (String) -> Unit) {
    val floors by viewModel.floorPlans.collectAsState()
    var showAddFloorSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddFloorSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Floor")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(text = "My Smart Home", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(floors) { floor ->
                    FloorSummaryCard(floor = floor, onClick = { onFloorClick(floor.id) })
                }
            }
        }
    }

    if (showAddFloorSheet) {
        AddFloorBottomSheet(
            onDismiss = { showAddFloorSheet = false },
            onConfirm = { name, level ->
                viewModel.addFloor(name, level)
                showAddFloorSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFloorBottomSheet(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var floorName by remember { mutableStateOf("") }
    var level by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add New Floor", style = MaterialTheme.typography.headlineSmall)
            
            OutlinedTextField(
                value = floorName,
                onValueChange = { floorName = it },
                label = { Text("Floor Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Attic, Basement") }
            )

            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text("Floor Level: $level", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.toInt() },
                    valueRange = -2f..5f,
                    steps = 6
                )
            }

            Button(
                onClick = { if (floorName.isNotBlank()) onConfirm(floorName, level) },
                modifier = Modifier.fillMaxWidth(),
                enabled = floorName.isNotBlank()
            ) {
                Text("Create Floor")
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
    
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var selectedX by remember { mutableIntStateOf(0) }
    var selectedY by remember { mutableIntStateOf(0) }
    var selectedWidth by remember { mutableIntStateOf(1) }
    var selectedHeight by remember { mutableIntStateOf(1) }

    // Drag state
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragEndOffset by remember { mutableStateOf<Offset?>(null) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Rooms", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Drag across dots to draw a room", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // Abstract grid representation of rooms
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStartOffset = offset
                                    dragEndOffset = offset
                                },
                                onDrag = { change, _ ->
                                    dragEndOffset = change.position
                                },
                                onDragEnd = {
                                    val start = dragStartOffset
                                    val end = dragEndOffset
                                    if (start != null && end != null) {
                                        val gridSize = 4
                                        val cellWidth = size.width / gridSize
                                        val cellHeight = size.height / gridSize

                                        val x1 = (start.x / cellWidth).toInt().coerceIn(0, gridSize - 1)
                                        val y1 = (start.y / cellHeight).toInt().coerceIn(0, gridSize - 1)
                                        val x2 = (end.x / cellWidth).toInt().coerceIn(0, gridSize - 1)
                                        val y2 = (end.y / cellHeight).toInt().coerceIn(0, gridSize - 1)

                                        val gx = min(x1, x2)
                                        val gy = min(y1, y2)
                                        val gw = (max(x1, x2) - gx + 1)
                                        val gh = (max(y1, y2) - gy + 1)

                                        // Check for overlap
                                        val hasOverlap = floor.rooms.any { room ->
                                            gx < room.x + room.width && gx + gw > room.x &&
                                            gy < room.y + room.height && gy + gh > room.y
                                        }

                                        if (!hasOverlap) {
                                            selectedX = gx
                                            selectedY = gy
                                            selectedWidth = gw
                                            selectedHeight = gh
                                            showAddRoomDialog = true
                                        }
                                    }
                                    dragStartOffset = null
                                    dragEndOffset = null
                                },
                                onDragCancel = {
                                    dragStartOffset = null
                                    dragEndOffset = null
                                }
                            )
                        }
                ) {
                    val gridSize = 4
                    val cellWidth = maxWidth / gridSize
                    val cellHeight = maxHeight / gridSize

                    // Draw the empty grid dots
                    for (i in 0 until gridSize) {
                        for (j in 0 until gridSize) {
                            Surface(
                                modifier = Modifier
                                    .offset(x = cellWidth * i + (cellWidth / 2) - 2.dp, y = cellHeight * j + (cellHeight / 2) - 2.dp)
                                    .size(4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {}
                        }
                    }

                    // Draw the rooms as blocks
                    floor.rooms.forEach { room ->
                        Card(
                            modifier = Modifier
                                .offset(x = cellWidth * room.x, y = cellHeight * room.y)
                                .size(width = cellWidth * room.width, height = cellHeight * room.height)
                                .padding(2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = room.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Draw the "Ghost" selection box during drag
                    if (dragStartOffset != null && dragEndOffset != null) {
                        val start = dragStartOffset!!
                        val end = dragEndOffset!!
                        
                        val x1 = (start.x / (constraints.maxWidth / gridSize)).toInt().coerceIn(0, gridSize - 1)
                        val y1 = (start.y / (constraints.maxHeight / gridSize)).toInt().coerceIn(0, gridSize - 1)
                        val x2 = (end.x / (constraints.maxWidth / gridSize)).toInt().coerceIn(0, gridSize - 1)
                        val y2 = (end.y / (constraints.maxHeight / gridSize)).toInt().coerceIn(0, gridSize - 1)

                        val gx = min(x1, x2)
                        val gy = min(y1, y2)
                        val gw = (max(x1, x2) - gx + 1)
                        val gh = (max(y1, y2) - gy + 1)

                        // Check for overlap
                        val hasOverlap = floor.rooms.any { room ->
                            gx < room.x + room.width && gx + gw > room.x &&
                            gy < room.y + room.height && gy + gh > room.y
                        }
                        
                        val boxColor = if (hasOverlap) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                        Surface(
                            modifier = Modifier
                                .offset(x = cellWidth * gx, y = cellHeight * gy)
                                .size(width = cellWidth * gw, height = cellHeight * gh)
                                .padding(2.dp),
                            color = boxColor.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium,
                            border = androidx.compose.foundation.BorderStroke(2.dp, boxColor)
                        ) {}
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
                if (room.devices.isEmpty()) {
                    item {
                        Text(
                            text = "No devices in this room",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
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

    if (showAddRoomDialog) {
        AddRoomDialog(
            x = selectedX,
            y = selectedY,
            width = selectedWidth,
            height = selectedHeight,
            onDismiss = { showAddRoomDialog = false },
            onConfirm = { name ->
                viewModel.addRoom(floor.id, name, selectedX, selectedY, selectedWidth, selectedHeight)
                showAddRoomDialog = false
            }
        )
    }
}

@Composable
fun AddRoomDialog(
    x: Int, y: Int, 
    width: Int, height: Int, 
    onDismiss: () -> Unit, 
    onConfirm: (String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Room: ${width}x${height} at ($x, $y)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a name for the new room.")
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(roomName) },
                enabled = roomName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
