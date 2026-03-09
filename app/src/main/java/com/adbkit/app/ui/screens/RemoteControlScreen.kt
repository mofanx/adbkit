package com.adbkit.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.RemoteControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onMenuClick: () -> Unit,
    viewModel: RemoteControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // If connected, show mirror view; otherwise show settings
    if (uiState.isConnected) {
        ScreenMirrorView(viewModel = viewModel, onMenuClick = onMenuClick)
    } else {
        SettingsView(viewModel = viewModel, onMenuClick = onMenuClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenMirrorView(
    viewModel: RemoteControlViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = {
                        Text("${strings.remoteControl} - ${uiState.fps}fps", style = MaterialTheme.typography.titleSmall)
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showControls = false }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = strings.fullscreen)
                        }
                        IconButton(onClick = { viewModel.startRemoteControl() }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = strings.disconnect)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showControls && uiState.navBarPosition != "hidden") {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(onClick = { viewModel.sendKey(4) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.btnBack)
                        }
                        FilledTonalButton(onClick = { viewModel.sendKey(3) }) {
                            Icon(Icons.Filled.Home, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.btnHome)
                        }
                        FilledTonalButton(onClick = { viewModel.sendKey(187) }) {
                            Icon(Icons.AutoMirrored.Filled.ViewList, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.btnRecent)
                        }
                        IconButton(onClick = { viewModel.sendKey(25) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, strings.keyVolDown)
                        }
                        IconButton(onClick = { viewModel.sendKey(24) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, strings.keyVolUp)
                        }
                        IconButton(onClick = { viewModel.sendKey(26) }) {
                            Icon(Icons.Filled.PowerSettingsNew, strings.keyPower)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = uiState.screenBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = strings.remoteControl,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (viewSize.width > 0 && viewSize.height > 0) {
                                        val xRatio = offset.x / viewSize.width
                                        val yRatio = offset.y / viewSize.height
                                        viewModel.sendTapAt(xRatio, yRatio)
                                    }
                                },
                                onLongPress = {
                                    showControls = !showControls
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> dragStart = offset },
                                onDragEnd = {},
                                onDrag = { change, _ ->
                                    val end = change.position
                                    if (viewSize.width > 0 && viewSize.height > 0) {
                                        val x1 = dragStart.x / viewSize.width
                                        val y1 = dragStart.y / viewSize.height
                                        val x2 = end.x / viewSize.width
                                        val y2 = end.y / viewSize.height
                                        viewModel.sendSwipeAt(x1, y1, x2, y2, 100)
                                        dragStart = end
                                    }
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.gettingScreen, color = Color.White)
                }
            }

            // FPS overlay
            if (!showControls) {
                Text(
                    text = strings.fpsOverlay(uiState.fps),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsView(
    viewModel: RemoteControlViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenRemoteControl) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Settings card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        label = strings.refreshRate,
                        value = when (uiState.refreshInterval) {
                            500L -> strings.refreshRateLow
                            200L -> strings.refreshRateMid
                            100L -> strings.refreshRateHigh
                            50L -> strings.refreshRateUltra
                            else -> strings.refreshRateMid
                        },
                        options = listOf(strings.refreshRateLow, strings.refreshRateMid, strings.refreshRateHigh, strings.refreshRateUltra),
                        onValueChange = { viewModel.setRefreshRate(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val aspectMap = mapOf(
                        "original" to strings.keepOriginal,
                        "16:9" to "16:9",
                        "4:3" to "4:3",
                        "adaptive" to strings.adaptive
                    )
                    val aspectReverseMap = aspectMap.entries.associate { (k, v) -> v to k }
                    SettingRow(
                        label = strings.aspectRatio,
                        value = aspectMap[uiState.aspectRatio] ?: uiState.aspectRatio,
                        options = aspectMap.values.toList(),
                        onValueChange = { viewModel.setAspectRatio(aspectReverseMap[it] ?: it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val navMap = mapOf(
                        "floating" to strings.floating,
                        "bottom" to strings.bottom,
                        "hidden" to strings.hidden
                    )
                    val navReverseMap = navMap.entries.associate { (k, v) -> v to k }
                    SettingRow(
                        label = strings.navBarPosition,
                        value = navMap[uiState.navBarPosition] ?: uiState.navBarPosition,
                        options = navMap.values.toList(),
                        onValueChange = { viewModel.setNavBarPosition(navReverseMap[it] ?: it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = strings.fullscreenDisplay,
                        checked = uiState.fullscreen,
                        onCheckedChange = { viewModel.setFullscreen(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = strings.screenOffControl,
                        checked = uiState.screenOff,
                        onCheckedChange = { viewModel.setScreenOff(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = strings.compatMode,
                        checked = uiState.compatMode,
                        onCheckedChange = { viewModel.setCompatMode(it) }
                    )
                }
            }

            // Tips card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.usageGuide, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.usageTip1, style = MaterialTheme.typography.bodySmall)
                    Text(strings.usageTip2, style = MaterialTheme.typography.bodySmall)
                    Text(strings.usageTip3, style = MaterialTheme.typography.bodySmall)
                    Text(strings.usageTip4, style = MaterialTheme.typography.bodySmall)
                    Text(strings.usageTip5, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Connect button
            Button(
                onClick = { viewModel.startRemoteControl() },
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

            // Status
            if (uiState.statusMessage.isNotEmpty()) {
                Text(
                    text = uiState.statusMessage,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
