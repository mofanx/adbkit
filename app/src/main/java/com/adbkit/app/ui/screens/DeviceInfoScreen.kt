package com.adbkit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.data.DeviceInfoSnapshot
import com.adbkit.app.ui.components.EmptyState
import com.adbkit.app.ui.components.LoadingState
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
    val context = LocalContext.current

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
                    IconButton(onClick = { viewModel.showHistoryDialog() }) {
                        Icon(Icons.Filled.History, contentDescription = strings.history)
                    }
                    IconButton(onClick = { viewModel.copyAll() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = strings.copyAll)
                    }
                    var shareMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { shareMenuExpanded = true }) {
                            Icon(Icons.Filled.Share, contentDescription = strings.share)
                        }
                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.share) },
                                onClick = {
                                    shareMenuExpanded = false
                                    val text = viewModel.buildShareText()
                                    if (text.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, strings.share))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.shareJson) },
                                onClick = {
                                    shareMenuExpanded = false
                                    val json = viewModel.buildShareJson()
                                    if (json.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, json)
                                        }
                                        context.startActivity(Intent.createChooser(intent, strings.shareJson))
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(
                message = strings.gettingDeviceInfo,
                modifier = Modifier.padding(padding)
            )
        } else if (uiState.error.isNotEmpty()) {
            EmptyState(
                title = uiState.error,
                actionLabel = strings.refresh,
                onAction = { viewModel.refresh() },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Storage usage card
                if (uiState.storageInfo.totalBytes > 0) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.storageInfo,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${formatBytes(uiState.storageInfo.usedBytes)} / ${formatBytes(uiState.storageInfo.totalBytes)}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { uiState.storageInfo.usedPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = when {
                                        uiState.storageInfo.usedPercent > 0.9f -> MaterialTheme.colorScheme.error
                                        uiState.storageInfo.usedPercent > 0.75f -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${strings.storageUsed}: ${formatBytes(uiState.storageInfo.usedBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${strings.storageFree}: ${formatBytes(uiState.storageInfo.availableBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

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

    if (uiState.showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideHistoryDialog() },
            title = { Text(strings.history) },
            text = {
                if (uiState.history.isEmpty()) {
                    Text(strings.noHistory, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (uiState.history.size > 1) {
                            item {
                                DeviceHistoryChart(uiState.history)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        items(uiState.history) { snapshot ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = snapshot.timeText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${strings.diBatteryLevel}: ${snapshot.batteryLevel.ifEmpty { "N/A" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${strings.diBatteryTemperature}: ${snapshot.batteryTemp.ifEmpty { "N/A" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Storage: ${formatBytes(snapshot.usedStorage)} / ${formatBytes(snapshot.totalStorage)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideHistoryDialog() }) {
                    Text(strings.close)
                }
            },
            dismissButton = {
                if (uiState.history.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text(strings.clearHistory)
                    }
                }
            }
        )
    }
}

@Composable
fun DeviceHistoryChart(history: List<DeviceInfoSnapshot>) {
    val batteryColor = MaterialTheme.colorScheme.primary
    val storageColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(8.dp)
    ) {
        val width = size.width
        val height = size.height
        val padding = 24.dp.toPx()
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        // Draw axes
        drawLine(
            color = textColor,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = 2f
        )
        drawLine(
            color = textColor,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = 2f
        )

        val maxBattery = 100f

        fun batteryPoint(index: Int, snapshot: DeviceInfoSnapshot): Offset {
            val level = snapshot.batteryLevel.replace("%", "").toFloatOrNull() ?: 0f
            val x = if (history.size > 1) {
                padding + chartWidth * (index / (history.size - 1).toFloat())
            } else padding + chartWidth / 2
            val y = height - padding - chartHeight * (level / maxBattery)
            return Offset(x, y)
        }

        fun storagePoint(index: Int, snapshot: DeviceInfoSnapshot): Offset {
            val used = if (snapshot.totalStorage > 0) snapshot.usedStorage.toFloat() / snapshot.totalStorage else 0f
            val x = if (history.size > 1) {
                padding + chartWidth * (index / (history.size - 1).toFloat())
            } else padding + chartWidth / 2
            val y = height - padding - chartHeight * used
            return Offset(x, y)
        }

        for (i in 0 until history.lastIndex) {
            drawLine(
                color = batteryColor,
                start = batteryPoint(i, history[i]),
                end = batteryPoint(i + 1, history[i + 1]),
                strokeWidth = 3f
            )
            drawLine(
                color = storageColor,
                start = storagePoint(i, history[i]),
                end = storagePoint(i + 1, history[i + 1]),
                strokeWidth = 3f
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(batteryColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Battery", style = MaterialTheme.typography.bodySmall, color = textColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(storageColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Storage", style = MaterialTheme.typography.bodySmall, color = textColor)
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

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))}GB"
    }
}
