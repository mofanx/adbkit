package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.DeviceInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onMenuClick: () -> Unit,
    viewModel: DeviceInfoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenDeviceInfo) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                    }
                    IconButton(onClick = { viewModel.copyAll() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = strings.copyAll)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.gettingDeviceInfo)
                }
            }
        } else if (uiState.error.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text(strings.retry)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Internal key -> localized label mapping
                val keyLabels = mapOf(
                    "model" to strings.diModel,
                    "brand" to strings.diBrand,
                    "device_name" to strings.diDeviceName,
                    "serial" to strings.diSerialNumber,
                    "hardware" to strings.diHardware,
                    "android_version" to strings.diAndroidVersion,
                    "sdk_version" to strings.diSdkVersion,
                    "build_id" to strings.diBuildId,
                    "security_patch" to strings.diSecurityPatch,
                    "baseband" to strings.diBasebandVersion,
                    "kernel" to strings.diKernelVersion,
                    "cpu_arch" to strings.diCpuArch,
                    "screen_resolution" to strings.diScreenResolution,
                    "screen_density" to strings.diScreenDensity,
                    "total_memory" to strings.diTotalMemory,
                    "available_memory" to strings.diAvailableMemory,
                    "total_storage" to strings.diTotalStorage,
                    "available_storage" to strings.diAvailableStorage,
                    "battery_level" to strings.diBatteryLevel,
                    "battery_status" to strings.diBatteryStatus,
                    "battery_temp" to strings.diBatteryTemperature,
                    "ip_address" to strings.diIpAddress,
                    "wifi_mac" to strings.diWifiMac,
                    "uptime" to strings.diUptime
                )

                // Group info by categories (using internal keys)
                val categories = listOf(
                    strings.basicInfo to listOf("model", "brand", "device_name", "serial", "hardware"),
                    strings.systemInfo to listOf("android_version", "sdk_version", "build_id", "security_patch", "baseband", "kernel"),
                    strings.hardwareInfo to listOf("cpu_arch", "screen_resolution", "screen_density", "total_memory", "available_memory", "total_storage", "available_storage"),
                    strings.batteryInfo to listOf("battery_level", "battery_status", "battery_temp"),
                    strings.networkInfo to listOf("ip_address", "wifi_mac"),
                    strings.otherInfo to listOf("uptime")
                )

                categories.forEach { (category, keys) ->
                    val filteredItems = keys.mapNotNull { key ->
                        uiState.deviceInfo[key]?.let { (keyLabels[key] ?: key) to it }
                    }
                    if (filteredItems.isNotEmpty()) {
                        item {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                        items(filteredItems) { (label, value) ->
                            InfoRow(label = label, value = value)
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Show any uncategorized info
                val allCategorized = categories.flatMap { it.second }.toSet()
                val uncategorized = uiState.deviceInfo.filter { it.key !in allCategorized }
                if (uncategorized.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.moreInfo,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                    items(uncategorized.toList()) { (key, value) ->
                        InfoRow(label = keyLabels[key] ?: key, value = value)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                text = value.ifEmpty { "N/A" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}
