package com.adbkit.app.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onNavigateToFastboot: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { viewModel.togglePairDialog() }) {
                        Icon(Icons.Filled.Link, contentDescription = "配对")
                    }
                    IconButton(onClick = onNavigateToFastboot) {
                        Icon(Icons.Filled.Star, contentDescription = "Fastboot")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                    Box {
                        IconButton(onClick = { viewModel.toggleMoreMenu() }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = uiState.showMoreMenu,
                            onDismissRequest = { viewModel.dismissMoreMenu() }
                        ) {
                            DropdownMenuItem(
                                text = { Text("重启 ADB 服务") },
                                onClick = {
                                    viewModel.dismissMoreMenu()
                                    viewModel.restartAdbServer()
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("断开所有设备") },
                                onClick = {
                                    viewModel.dismissMoreMenu()
                                    viewModel.disconnectAll()
                                },
                                leadingIcon = { Icon(Icons.Filled.LinkOff, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = {
                                    viewModel.dismissMoreMenu()
                                    onNavigateToSettings()
                                },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) }
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
            Spacer(modifier = Modifier.height(24.dp))

            // App Icon
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // IP Address input
            OutlinedTextField(
                value = uiState.ipAddress,
                onValueChange = { viewModel.updateIpAddress(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("192.168.1.100") },
                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.scanDevices() }) {
                        Icon(Icons.Filled.Search, contentDescription = "扫描")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

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
                Text("连接", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connected devices count
            if (uiState.connectedDevices.isNotEmpty()) {
                Text(
                    text = "已连接${uiState.connectedDevices.size}个设备",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.connectedDevices) { device ->
                    DeviceCard(
                        device = device,
                        isSelected = device == uiState.selectedDevice,
                        onClick = { viewModel.selectDevice(device) },
                        onDisconnect = { viewModel.disconnectDevice(device) }
                    )
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
                    text = if (device.contains(":")) "WiFi连接" else "USB连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Filled.LinkOff,
                    contentDescription = "断开",
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
        title = { Text("无线配对") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Android 11+ 支持无线调试配对。请在设备设置 > 开发者选项 > 无线调试中获取配对信息。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pairCode,
                    onValueChange = { pairCode = it },
                    label = { Text("配对码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(ip, port, pairCode) }) {
                Text("配对")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
