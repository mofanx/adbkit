package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.components.EmptyDevicePlaceholder
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.ProcessManagerUiState
import com.adbkit.app.ui.viewmodel.ProcessManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManagerScreen(
    onMenuClick: () -> Unit,
    viewModel: ProcessManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    // Auto-refresh CPU/memory/process list every 5 seconds while this screen is active
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (!uiState.isLoading) {
                viewModel.refresh()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenProcessManager) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                    }
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = strings.search)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row: Running Apps / All Processes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.showAppsOnly,
                    onClick = { viewModel.setShowAppsOnly(true) },
                    label = { Text(strings.runningApps) }
                )
                FilterChip(
                    selected = !uiState.showAppsOnly,
                    onClick = { viewModel.setShowAppsOnly(false) },
                    label = { Text(strings.allProcesses) }
                )
            }

            // Search bar
            if (uiState.showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(strings.searchProcess) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                Icon(Icons.Filled.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
            }

            // Sort chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sortLabels = listOf(
                    ProcessManagerUiState.SortMode.MEMORY to strings.memory,
                    ProcessManagerUiState.SortMode.PID to strings.pid,
                    ProcessManagerUiState.SortMode.NAME to strings.processName,
                    ProcessManagerUiState.SortMode.CPU to strings.cpu
                )
                sortLabels.forEach { (mode, label) ->
                    FilterChip(
                        selected = uiState.sortMode == mode,
                        onClick = { viewModel.setSortMode(mode) },
                        label = { Text(label) }
                    )
                }
                IconButton(onClick = { viewModel.setSortMode(uiState.sortMode) }) {
                    Icon(
                        imageVector = if (uiState.sortAscending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (uiState.sortAscending) "Ascending" else "Descending"
                    )
                }
            }

            // Memory overview card
            if (uiState.memoryInfo.totalKb > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.memoryUsage, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${formatMemSize(uiState.memoryInfo.usedKb)} / ${formatMemSize(uiState.memoryInfo.totalKb)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { uiState.memoryInfo.usedPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (uiState.memoryInfo.usedPercent > 0.85f) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${strings.usedMemory}: ${formatMemSize(uiState.memoryInfo.usedKb)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${strings.freeMemory}: ${formatMemSize(uiState.memoryInfo.availableKb)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Status message
            if (uiState.statusMessage.isNotEmpty()) {
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error.isNotEmpty()) {
                EmptyDevicePlaceholder(
                    onRetry = { viewModel.refresh() },
                    message = uiState.error
                )
            } else if (uiState.showAppsOnly) {
                // Running apps view
                val apps = uiState.filteredApps
                Text(
                    text = strings.runningAppsCount(apps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(apps) { app ->
                        RunningAppCard(
                            app = app,
                            onForceStop = { viewModel.forceStopApp(app["name"] ?: "") },
                            onKill = { viewModel.killProcess(app["pid"] ?: "") },
                            onDetails = { viewModel.showProcessDetails(app["pid"] ?: "", app["name"] ?: "") }
                        )
                    }
                }
            } else {
                // All processes view
                Text(
                    text = strings.totalProcesses(uiState.filteredProcesses.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.pid, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp))
                    Text(strings.memory, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp))
                    Text(strings.processName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(48.dp))
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredProcesses) { process ->
                        ProcessRow(
                            process = process,
                            onKill = { viewModel.killProcess(process["pid"] ?: "") },
                            onDetails = { viewModel.showProcessDetails(process["pid"] ?: "", process["name"] ?: "") }
                        )
                    }
                }
            }
        }

        // Process details dialog
        uiState.processDetails?.let { details ->
            ProcessDetailsDialog(
                details = details,
                onDismiss = { viewModel.dismissProcessDetails() }
            )
        }
    }
}

@Composable
fun RunningAppCard(
    app: Map<String, String>,
    onForceStop: () -> Unit,
    onKill: () -> Unit,
    onDetails: () -> Unit = {}
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app["name"] ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "PID: ${app["pid"] ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${strings.memory}: ${formatMemory(app["memory"] ?: "0")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            FilledTonalButton(
                onClick = onForceStop,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(strings.appForceStop, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDetails, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = strings.details,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onKill, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = strings.killProcess,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ProcessRow(
    process: Map<String, String>,
    onKill: () -> Unit,
    onDetails: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = process["pid"] ?: "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = formatMemory(process["memory"] ?: "0"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = process["name"] ?: "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDetails, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Info,
                contentDescription = LocalStrings.current.details,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onKill, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = LocalStrings.current.killProcess,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ProcessDetailsDialog(
    details: com.adbkit.app.ui.viewmodel.ProcessDetails,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${details.name} (${details.pid})") },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (details.error.isNotEmpty()) {
                        Text(text = details.error, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(text = "${strings.detailsPid}: ${details.pid}")
                        Text(text = "${strings.detailsPpid}: ${details.ppid}")
                        Text(text = "${strings.detailsThreads}: ${details.threads}")
                        Text(text = "${strings.detailsCpuTime}: ${details.cpuTime}")
                        Text(text = "${strings.detailsResidentPages}: ${details.residentPages}")
                        Text(
                            text = "${strings.detailsCommandLine}: ${details.commandLine}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

private fun formatMemory(kbStr: String): String {
    val kb = kbStr.toLongOrNull() ?: return kbStr
    return formatMemSize(kb)
}

private fun formatMemSize(kb: Long): String {
    return when {
        kb < 1024 -> "${kb}K"
        kb < 1024 * 1024 -> "${kb / 1024}M"
        else -> "${"%.1f".format(kb.toDouble() / (1024 * 1024))}G"
    }
}
