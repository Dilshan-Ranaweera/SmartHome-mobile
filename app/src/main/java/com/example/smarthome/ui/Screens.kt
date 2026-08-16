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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(deviceId: String, viewModel: HomeViewModel, onBack: () -> Unit) {
    val devices by viewModel.devices.collectAsState()
    val device = devices.find { it.id == deviceId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (device == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Device not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.status == DeviceStatus.ON) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Status", style = MaterialTheme.typography.labelLarge)
                            Text(
                                device.status.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (device.status == DeviceStatus.ON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (device !is Device.SecurityCamera) {
                            Switch(
                                checked = device.status == DeviceStatus.ON,
                                onCheckedChange = { viewModel.toggleDevice(device.id, it) }
                            )
                        }
                    }
                }

                // Type-specific Controls
                when (device) {
                    is Device.Light -> LightDetailControls(device, viewModel)
                    is Device.SafetyDevice -> SafetyDetailControls(device, viewModel)
                    is Device.SecurityCamera -> CameraDetailControls(device)
                    is Device.MultiSwitch -> MultiSwitchDetailControls(device, viewModel)
                    is Device.Outlet -> OutletDetailControls(device)
                }
            }
        }
    }
}

@Composable
fun LightDetailControls(device: Device.Light, viewModel: HomeViewModel) {
    var startTime by remember(device.id) { mutableStateOf(device.scheduleStart ?: "18:00") }
    var endTime by remember(device.id) { mutableStateOf(device.scheduleEnd ?: "06:00") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Automation Schedule", style = MaterialTheme.typography.titleLarge)
        Text("The light will automatically turn ON during this time window.", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text("Start Time") },
                modifier = Modifier.weight(1f),
                placeholder = { Text("HH:mm") }
            )
            OutlinedTextField(
                value = endTime,
                onValueChange = { endTime = it },
                label = { Text("End Time") },
                modifier = Modifier.weight(1f),
                placeholder = { Text("HH:mm") }
            )
        }

        Button(
            onClick = { viewModel.updateLightSchedule(device.id, startTime, endTime) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Schedule")
        }
    }
}

@Composable
fun SafetyDetailControls(device: Device.SafetyDevice, viewModel: HomeViewModel) {
    var maxMinutes by remember(device.id) { mutableIntStateOf(device.maxOnDurationMinutes) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Safety Configuration", style = MaterialTheme.typography.titleLarge)
        
        if (device.status == DeviceStatus.ON && device.lastTurnedOnAt != null) {
            val elapsed = ((System.currentTimeMillis() - device.lastTurnedOnAt) / 60000).toInt()
            val remaining = (device.maxOnDurationMinutes - elapsed).coerceAtLeast(0)
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Time Until Auto-Off", style = MaterialTheme.typography.labelLarge)
                    Text("$remaining minutes", style = MaterialTheme.typography.displayMedium)
                    LinearProgressIndicator(
                        progress = { (elapsed.toFloat() / device.maxOnDurationMinutes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Text("Max ON Duration: $maxMinutes minutes", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = maxMinutes.toFloat(),
            onValueChange = { maxMinutes = it.toInt() },
            valueRange = 5f..60f,
            steps = 10
        )

        Button(
            onClick = { viewModel.updateSafetyDuration(device.id, maxMinutes) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Safety Limit")
        }
    }
}

@Composable
fun CameraDetailControls(device: Device.SecurityCamera) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Live Stream", style = MaterialTheme.typography.titleLarge)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(androidx.compose.ui.graphics.Color.Black, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            if (device.status != DeviceStatus.DISCONNECTED) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(48.dp))
                    Text("LIVE FEED ACTIVE", color = androidx.compose.ui.graphics.Color.White)
                    Text(device.streamUri ?: "No URI", color = androidx.compose.ui.graphics.Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("CAMERA DISCONNECTED", color = MaterialTheme.colorScheme.error)
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Record") }, leadingIcon = { Icon(Icons.Default.RadioButtonChecked, null) })
            AssistChip(onClick = {}, label = { Text("Snapshot") }, leadingIcon = { Icon(Icons.Default.PhotoCamera, null) })
            AssistChip(onClick = {}, label = { Text("Talk") }, leadingIcon = { Icon(Icons.Default.Mic, null) })
        }
    }
}

@Composable
fun MultiSwitchDetailControls(device: Device.MultiSwitch, viewModel: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Individual Switch Control", style = MaterialTheme.typography.titleLarge)
        
        device.switches.forEach { sw ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (sw.isOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sw.name, style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = sw.isOn,
                        onCheckedChange = { viewModel.toggleMultiSwitch(device.id, sw.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun OutletDetailControls(device: Device.Outlet) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Energy Usage History", style = MaterialTheme.typography.titleLarge)
        Text("This outlet is currently monitoring real-time power consumption.", style = MaterialTheme.typography.bodyMedium)
        
        // Placeholder for a small chart or graph
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Text("Power Graph Placeholder", color = MaterialTheme.colorScheme.outline)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(viewModel: HomeViewModel) {
    val devices by viewModel.devices.collectAsState()
    val lights = devices.filterIsInstance<Device.Light>()
    val safetyDevices = devices.filterIsInstance<Device.SafetyDevice>()

    var editingLight by remember { mutableStateOf<Device.Light?>(null) }
    var editingSafety by remember { mutableStateOf<Device.SafetyDevice?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(text = "Schedules & Safety", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Manage light automation and safety cutoffs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }
        }

        // Light Schedules Section
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Light Schedules", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (lights.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No lights added yet. Add lights to rooms to set schedules.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(lights) { light ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (light.status == DeviceStatus.ON)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = if (light.status == DeviceStatus.ON) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = light.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = light.status.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (light.status == DeviceStatus.ON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = light.status == DeviceStatus.ON,
                                onCheckedChange = { viewModel.toggleDevice(light.id, it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Schedule", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                if (!light.scheduleStart.isNullOrEmpty() && !light.scheduleEnd.isNullOrEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${light.scheduleStart} → ${light.scheduleEnd}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    }
                                } else {
                                    Text(text = "No schedule set", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            FilledTonalButton(onClick = { editingLight = light }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }

        // Safety Auto-Shutoffs Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Safety Auto-Shutoffs", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (safetyDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No safety devices added yet.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(safetyDevices) { safety ->
                val isOn = safety.status == DeviceStatus.ON
                val elapsedMinutes = if (isOn && safety.lastTurnedOnAt != null) {
                    ((System.currentTimeMillis() - safety.lastTurnedOnAt) / 60000).toInt()
                } else 0
                val remaining = safety.maxOnDurationMinutes - elapsedMinutes
                val progress = if (safety.maxOnDurationMinutes > 0 && isOn) {
                    (elapsedMinutes.toFloat() / safety.maxOnDurationMinutes).coerceIn(0f, 1f)
                } else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Iron,
                                    contentDescription = null,
                                    tint = if (isOn) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = safety.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (isOn) "ON — Auto-off in ${remaining.coerceAtLeast(0)} min" else "OFF",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = isOn,
                                onCheckedChange = { viewModel.toggleDevice(safety.id, it) }
                            )
                        }

                        if (isOn) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "$elapsedMinutes min elapsed", style = MaterialTheme.typography.labelSmall)
                                Text(text = "${safety.maxOnDurationMinutes} min max", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Max ON Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${safety.maxOnDurationMinutes} minutes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                            }
                            FilledTonalButton(onClick = { editingSafety = safety }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Light Schedule Dialog
    if (editingLight != null) {
        val light = editingLight!!
        var start by remember(light.id) { mutableStateOf(light.scheduleStart ?: "18:00") }
        var end by remember(light.id) { mutableStateOf(light.scheduleEnd ?: "06:00") }

        AlertDialog(
            onDismissRequest = { editingLight = null },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            title = { Text("Edit Schedule: ${light.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Set the time window when this light should automatically turn ON.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start Time (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 18:00") }
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End Time (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 06:00") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateLightSchedule(light.id, start, end)
                    editingLight = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingLight = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Safety Duration Dialog
    if (editingSafety != null) {
        val safety = editingSafety!!
        var maxMin by remember(safety.id) { mutableIntStateOf(safety.maxOnDurationMinutes) }

        AlertDialog(
            onDismissRequest = { editingSafety = null },
            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
            title = { Text("Edit Safety: ${safety.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Set the maximum duration this device can stay ON before automatic shutoff.", style = MaterialTheme.typography.bodyMedium)
                    Text("Max Duration: $maxMin minutes", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = maxMin.toFloat(),
                        onValueChange = { maxMin = it.toInt() },
                        valueRange = 1f..120f,
                        steps = 23
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1 min", style = MaterialTheme.typography.labelSmall)
                        Text("120 min", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSafetyDuration(safety.id, maxMin)
                    editingSafety = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingSafety = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CamerasScreen(viewModel: HomeViewModel) {
    val devices by viewModel.devices.collectAsState()
    val cameras = devices.filterIsInstance<Device.SecurityCamera>()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(text = "Security Cameras", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Monitor your camera feeds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
            }
        }

        if (cameras.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No cameras added yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else {
            items(cameras) { camera ->
                val isConnected = camera.status != DeviceStatus.DISCONNECTED && camera.status != DeviceStatus.ERROR
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Camera feed placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(
                                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                                    MaterialTheme.shapes.medium
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isConnected) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Live Feed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.VideocamOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Disconnected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = camera.name, style = MaterialTheme.typography.titleMedium)
                                if (!camera.streamUri.isNullOrEmpty()) {
                                    Text(
                                        text = camera.streamUri,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1
                                    )
                                }
                            }
                            Badge(
                                containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (isConnected) "CONNECTED" else camera.status.name,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
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
