package com.adbkit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.service.AdbBinaryManager
import com.adbkit.app.ui.components.WelcomePlaceholder
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onNavigateToFastboot: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onDeviceClick: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAdbReady by AdbBinaryManager.adbReady.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { viewModel.togglePairDialog() }) {
                        Icon(Icons.Filled.Link, contentDescription = strings.pair)
                    }
                    IconButton(onClick = onNavigateToFastboot) {
                        Icon(Icons.Filled.FlashOn, contentDescription = strings.fastboot)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = strings.settings)
                    }
                    Box {
                        IconButton(onClick = { viewModel.toggleMoreMenu() }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = strings.more)
                        }
                        DropdownMenu(
                            expanded = uiState.showMoreMenu,
                            onDismissRequest = { viewModel.dismissMoreMenu() }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.restartAdbService) },
                                onClick = {
                                    viewModel.dismissMoreMenu()
                                    viewModel.restartAdbServer()
                                },
                                leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.disconnectAll) },
                                onClick = {
                                    viewModel.dismissMoreMenu()
                                    viewModel.disconnectAll()
                                },
                                leadingIcon = { Icon(Icons.Filled.LinkOff, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ADB not ready warning banner
            if (!isAdbReady) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.adbStatusNotReady,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onNavigateToSettings) {
                            Text(strings.settings)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Icon
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // IP Address input with history dropdown
            Box {
                OutlinedTextField(
                    value = uiState.ipAddress,
                    onValueChange = { viewModel.updateIpAddress(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = {
                        IconButton(onClick = { viewModel.toggleHistoryDropdown() }) {
                            Icon(Icons.Filled.History, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.showScanConfig() }) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = strings.scan)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    supportingText = {
                        if (uiState.localIp.isNotBlank()) {
                            Text("${strings.localIp}: ${uiState.localIp}")
                        }
                    }
                )

                // History dropdown
                DropdownMenu(
                    expanded = uiState.showHistoryDropdown,
                    onDismissRequest = { viewModel.dismissHistoryDropdown() }
                ) {
                    if (uiState.connectionHistory.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(strings.noHistory, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        uiState.connectionHistory.forEach { address ->
                            DropdownMenuItem(
                                text = {
                                    Text(address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = { viewModel.selectHistory(address) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeHistory(address) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = strings.delete,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connect button
            Button(
                onClick = { viewModel.connectDevice() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = !uiState.isConnecting
            ) {
                if (uiState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(strings.connect, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.connectedDevices.isEmpty() && !uiState.isConnecting) {
                WelcomePlaceholder(
                    onScan = { viewModel.showScanConfig() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Connected devices count
                if (uiState.connectedDevices.isNotEmpty()) {
                    Text(
                        text = strings.connectedDevices(uiState.connectedDevices.size),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Device list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(uiState.connectedDevices) { device ->
                        DeviceCard(
                            device = device,
                            isSelected = device == uiState.selectedDevice,
                            onClick = {
                                viewModel.selectDevice(device)
                                onDeviceClick(device)
                            },
                            onDisconnect = { viewModel.disconnectDevice(device) }
                        )
                    }
                }

                // Dashboard for the selected device
                if (uiState.connectedDevices.isNotEmpty() && !uiState.isLoadingDashboard) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DashboardCard(
                        info = uiState.dashboardInfo,
                        onRefresh = { viewModel.loadDashboard() }
                    )
                } else if (uiState.isLoadingDashboard) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // Status message
            if (uiState.statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.statusMessage,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Pair dialog
        if (uiState.showPairDialog) {
            WirelessPairDialog(
                onDismiss = { viewModel.togglePairDialog() },
                onPair = { ip, port, code -> viewModel.pairDevice(ip, port, code) }
            )
        }

        // Scan dialog
        if (uiState.showScanDialog) {
            ScanDevicesDialog(
                isScanning = uiState.isScanning,
                devices = uiState.scannedDevices,
                startPort = uiState.scanStartPort,
                endPort = uiState.scanEndPort,
                timeout = uiState.scanTimeoutMs,
                onDismiss = { viewModel.dismissScanDialog() },
                onConnect = { viewModel.connectScannedDevice(it) },
                onStartPortChange = { viewModel.updateScanStartPort(it) },
                onEndPortChange = { viewModel.updateScanEndPort(it) },
                onTimeoutChange = { viewModel.updateScanTimeoutMs(it) },
                onStartScan = { viewModel.scanDevices() }
            )
        }
    }
}

@Composable
fun DeviceCard(
    device: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val parts = device.split(":")
    val displayName = if (parts.size >= 2) {
        device
    } else {
        device
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (device.contains(":")) LocalStrings.current.wifiConnection else LocalStrings.current.usbConnection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Filled.LinkOff,
                    contentDescription = LocalStrings.current.disconnect,
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WirelessPairDialog(
    onDismiss: () -> Unit,
    onPair: (String, String, String) -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalStrings.current.wirelessPair) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    LocalStrings.current.wirelessPairDesc,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(LocalStrings.current.ipAddress) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(LocalStrings.current.port) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pairCode,
                    onValueChange = { pairCode = it },
                    label = { Text(LocalStrings.current.pairCode) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(ip, port, pairCode) }) {
                Text(LocalStrings.current.pair)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalStrings.current.cancel)
            }
        }
    )
}

@Composable
fun ScanDevicesDialog(
    isScanning: Boolean,
    devices: List<String>,
    startPort: String,
    endPort: String,
    timeout: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    onStartPortChange: (String) -> Unit,
    onEndPortChange: (String) -> Unit,
    onTimeoutChange: (String) -> Unit,
    onStartScan: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.scanLan) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.scanningLan)
                    }
                } else if (devices.isEmpty()) {
                    Text(
                        text = strings.noDevicesFound,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startPort,
                            onValueChange = onStartPortChange,
                            label = { Text(strings.startPort) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endPort,
                            onValueChange = onEndPortChange,
                            label = { Text(strings.endPort) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = onTimeoutChange,
                        label = { Text(strings.timeout) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.startScan)
                    }
                } else {
                    Text(
                        text = strings.scanResult(devices.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    devices.forEach { device ->
                        Surface(
                            onClick = { onConnect(device) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = device,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}

@Composable
fun DashboardCard(
    info: Map<String, String>,
    onRefresh: () -> Unit
) {
    val strings = LocalStrings.current
    val items = listOf(
        strings.diModel to (info["model"] ?: ""),
        strings.diAndroidVersion to (info["android_version"] ?: ""),
        strings.diBatteryLevel to (info["battery_level"] ?: ""),
        strings.diTotalMemory to (info["total_memory"] ?: ""),
        strings.diAvailableMemory to (info["available_memory"] ?: ""),
        strings.diTotalStorage to (info["total_storage"] ?: ""),
        strings.diAvailableStorage to (info["available_storage"] ?: ""),
        strings.diScreenResolution to (info["screen_resolution"] ?: ""),
        strings.diIpAddress to (info["ip_address"] ?: "")
    ).filter { it.second.isNotBlank() }

    if (items.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.deviceOverview,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
