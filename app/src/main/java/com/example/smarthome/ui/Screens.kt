package com.example.smarthome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.smarthome.domain.Device
import com.example.smarthome.domain.DeviceStatus
import com.example.smarthome.domain.FloorPlan
import com.example.smarthome.viewmodel.HomeViewModel
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: HomeViewModel, onFloorClick: (String) -> Unit) {
    val floors by viewModel.floorPlans.collectAsState()
    var showAddFloorSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFloorSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Floor")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(text = "My Smart Home", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (floors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No floors added yet. Click + to start.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(floors) { floor ->
                        FloorSummaryCard(floor = floor, onClick = { onFloorClick(floor.id) })
                    }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss
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
                onClick = { onConfirm(floorName, level) },
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(end = 8.dp)) {
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

    // Room and Device management state
    var selectedRoomIdForDevice by remember { mutableStateOf<String?>(null) }
    var showAddDeviceSheet by remember { mutableStateOf(false) }
    var showRoomDetailSheet by remember { mutableStateOf(false) }
    var selectedRoomIdForDetail by remember { mutableStateOf<String?>(null) }

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
                Column {
                    Text(text = "Rooms", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Drag to draw rooms. Tap existing rooms to add devices.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .pointerInput(floor.rooms) {
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

                    // Empty grid dots
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

                    // Rooms
                    floor.rooms.forEach { room ->
                        Card(
                            modifier = Modifier
                                .offset(x = cellWidth * room.x, y = cellHeight * room.y)
                                .size(width = cellWidth * room.width, height = cellHeight * room.height)
                                .padding(2.dp)
                                .clickable {
                                    selectedRoomIdForDevice = room.id
                                    showAddDeviceSheet = true
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(text = room.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Ghost selection box
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
                Text(text = "Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }

            floor.rooms.forEach { room ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            selectedRoomIdForDetail = room.id
                            showRoomDetailSheet = true
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = room.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.Add, contentDescription = "Add Device", modifier = Modifier.size(20.dp).clickable {
                            selectedRoomIdForDevice = room.id
                            showAddDeviceSheet = true
                        })
                    }
                }
                if (room.devices.isEmpty()) {
                    item {
                        Text(text = "No devices. Tap + to add.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                items(room.devices) { device ->
                    DeviceItem(
                        device = device,
                        onToggle = { isOn -> viewModel.toggleDevice(device.id, isOn) },
                        onMultiToggle = { swId, isOn -> viewModel.toggleMultiSwitch(device.id, swId, isOn) },
                        onClick = { onDeviceClick(device.id) }
                    )
                }
            }
        }
    }

    if (showAddRoomDialog) {
        AddRoomDialog(
            x = selectedX, y = selectedY, width = selectedWidth, height = selectedHeight,
            onDismiss = { showAddRoomDialog = false },
            onConfirm = { name ->
                viewModel.addRoom(floor.id, name, selectedX, selectedY, selectedWidth, selectedHeight)
                showAddRoomDialog = false
            }
        )
    }

    if (showAddDeviceSheet && selectedRoomIdForDevice != null) {
        AddDeviceBottomSheet(
            onDismiss = { showAddDeviceSheet = false },
            onConfirm = { device ->
                viewModel.addDeviceToRoom(selectedRoomIdForDevice!!, device)
                showAddDeviceSheet = false
            }
        )
    }

    if (showRoomDetailSheet && selectedRoomIdForDetail != null) {
        val selectedRoom = floor.rooms.find { it.id == selectedRoomIdForDetail }
        if (selectedRoom != null) {
            RoomDetailBottomSheet(
                room = selectedRoom,
                onDismiss = { showRoomDetailSheet = false },
                viewModel = viewModel,
                onDeviceClick = onDeviceClick
            )
        }
    }
}

@Composable
fun AddRoomDialog(x: Int, y: Int, width: Int, height: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var roomName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Room: ${width}x${height}") },
        text = {
            OutlinedTextField(
                value = roomName, onValueChange = { roomName = it },
                label = { Text("Room Name") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(roomName) }, enabled = roomName.isNotBlank()) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceBottomSheet(onDismiss: () -> Unit, onConfirm: (Device) -> Unit) {
    var deviceType by remember { mutableStateOf("Outlet") }
    var deviceName by remember { mutableStateOf("") }
    var switchCount by remember { mutableIntStateOf(2) }
    var maxDuration by remember { mutableIntStateOf(15) }
    var startTime by remember { mutableStateOf("18:00") }
    var endTime by remember { mutableStateOf("06:00") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add New Device", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("Device Name") }, modifier = Modifier.fillMaxWidth())
            Text("Device Type", style = MaterialTheme.typography.titleMedium)
            
            val types = listOf("Outlet", "Multi-Switch", "Safety Iron", "Light", "Camera")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // First Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.take(3).forEach { type ->
                        FilterChip(
                            selected = deviceType == type,
                            onClick = { deviceType = type },
                            label = { Text(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Second Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.drop(3).forEach { type ->
                        FilterChip(
                            selected = deviceType == type,
                            onClick = { deviceType = type },
                            label = { Text(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f)) // Placeholder to keep layout consistent
                }
            }

            when (deviceType) {
                "Multi-Switch" -> Column {
                    Text("Number of Switches: $switchCount")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 5).forEach { count ->
                            FilterChip(selected = switchCount == count, onClick = { switchCount = count }, label = { Text(count.toString()) })
                        }
                    }
                }
                "Safety Iron" -> Column {
                    Text("Max Duration: $maxDuration minutes")
                    Slider(value = maxDuration.toFloat(), onValueChange = { maxDuration = it.toInt() }, valueRange = 5f..60f, steps = 11)
                }
                "Light" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automation Schedule")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start Time") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End Time") }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Button(
                onClick = {
                    val id = UUID.randomUUID().toString()
                    val newDevice = when (deviceType) {
                        "Outlet" -> Device.Outlet(id, deviceName)
                        "Multi-Switch" -> Device.MultiSwitch(id, deviceName, switches = List(switchCount) { i -> com.example.smarthome.domain.SwitchUnit("s$i", "Switch ${i + 1}", false) })
                        "Safety Iron" -> Device.SafetyDevice(id, deviceName, maxOnDurationMinutes = maxDuration)
                        "Light" -> Device.Light(id, deviceName, scheduleStart = startTime, scheduleEnd = endTime)
                        "Camera" -> Device.SecurityCamera(id, deviceName, streamUri = "mock://stream/$deviceName")
                        else -> Device.Outlet(id, deviceName)
                    }
                    onConfirm(newDevice)
                },
                modifier = Modifier.fillMaxWidth(), enabled = deviceName.isNotBlank()
            ) { Text("Add Device") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailBottomSheet(room: com.example.smarthome.domain.Room, onDismiss: () -> Unit, viewModel: HomeViewModel, onDeviceClick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
            Text(text = room.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Devices", style = MaterialTheme.typography.titleMedium)
            if (room.devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No devices", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    items(room.devices) { device ->
                        DeviceItem(device = device, onToggle = { viewModel.toggleDevice(device.id, it) }, onMultiToggle = { swId, isOn -> viewModel.toggleMultiSwitch(device.id, swId, isOn) }, onClick = { onDeviceClick(device.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, onToggle: (Boolean) -> Unit, onMultiToggle: (String, Boolean) -> Unit = { _, _ -> }, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (device.status == DeviceStatus.ON) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            ListItem(
                headlineContent = { Text(device.name) }, supportingContent = { Text(device.status.name) },
                leadingContent = {
                    val icon = when (device) {
                        is Device.Outlet -> Icons.Default.Outlet
                        is Device.MultiSwitch -> Icons.Default.Hub
                        is Device.SafetyDevice -> Icons.Default.Iron
                        is Device.SecurityCamera -> Icons.Default.Videocam
                        is Device.Light -> Icons.Default.Lightbulb
                    }
                    Icon(icon, contentDescription = null, tint = if (device.status == DeviceStatus.ON) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                },
                trailingContent = {
                    if (device !is Device.SecurityCamera && device !is Device.MultiSwitch) {
                        Switch(checked = device.status == DeviceStatus.ON, onCheckedChange = onToggle)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
            if (device is Device.MultiSwitch) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    device.switches.forEach { sw ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = sw.name, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = sw.isOn, onCheckedChange = { onMultiToggle(sw.id, it) })
                        }
                    }
                }
            }
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
    val reports by viewModel.usageReports.collectAsState()

    val totalEnergy = reports.sumOf { it.powerConsumedWh } / 1000.0
    val totalTimeMinutes = reports.sumOf { it.durationMinutes }
    val devicesUsed = reports.map { it.deviceName }.distinct().size
    val safetyEvents = reports.count { it.deviceType == "SafetyDevice" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Usage Reports", style = MaterialTheme.typography.headlineMedium)
                Text(text = "Analytics & Energy Consumption", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }
        }

        // Summary Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(title = "Energy Used", value = "%.2f kWh".format(totalEnergy), icon = Icons.Default.Bolt, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer)
                    SummaryCard(title = "Total Time", value = "${totalTimeMinutes / 60}h ${totalTimeMinutes % 60}m", icon = Icons.Default.Schedule, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(title = "Devices Used", value = "$devicesUsed", icon = Icons.Default.Devices, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    SummaryCard(title = "Safety Events", value = "$safetyEvents", icon = Icons.Default.Shield, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.errorContainer)
                }
            }
        }

        // Usage Overview Chart
        item {
            Column {
                Text(text = "Usage Overview", style = MaterialTheme.typography.titleLarge)
                Text(text = "Energy Consumption", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                UsageBarChart()
            }
        }

        // Top Consumers
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Top Energy Consumers", style = MaterialTheme.typography.titleLarge)
                
                val topConsumers = reports.groupBy { it.deviceName }
                    .mapValues { entry -> entry.value.sumOf { it.powerConsumedWh } / 1000.0 }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(4)

                val maxEnergy = topConsumers.firstOrNull()?.second ?: 1.0

                topConsumers.forEach { (name, energy) ->
                    EnergyConsumerItem(name, energy, energy / maxEnergy)
                }
            }
        }

        // Safety & Automation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Safety & Automation", style = MaterialTheme.typography.titleMedium)
                    SafetyStatRow("Automatic Shutoffs", "1")
                    SafetyStatRow("Scheduled Activations", "6")
                    SafetyStatRow("Safety Alerts", "1")
                }
            }
        }

        // Recent Activity
        item {
            Text(text = "Recent Activity", style = MaterialTheme.typography.titleLarge)
        }

        items(reports.sortedByDescending { it.timestamp }) { report ->
            UsageReportItem(report)
        }
    }
}

@Composable
fun UsageBarChart() {
    val data = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.3f)
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, value ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(value)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = days[index], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun EnergyConsumerItem(name: String, energy: Double, progress: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "%.2f kWh".format(energy),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(60.dp)
            )
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun SafetyStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun UsageReportItem(report: com.example.smarthome.domain.UsageReport) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = report.deviceName, style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (report.deviceType == "SafetyDevice") "Automatically turned OFF" else "Turned ON",
            style = MaterialTheme.typography.bodySmall,
            color = if (report.deviceType == "SafetyDevice") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${report.durationMinutes} min · ${report.powerConsumedWh.toInt()} Wh",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = if (System.currentTimeMillis() - report.timestamp < 3600000) "Just now" else "Yesterday",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
fun RoomCard(name: String, deviceCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(text = "$deviceCount Devices", style = MaterialTheme.typography.bodySmall)
        }
    }
}
