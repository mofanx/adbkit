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
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = {
                        Text("远程控制 - ${uiState.fps}fps", style = MaterialTheme.typography.titleSmall)
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showControls = false }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "全屏")
                        }
                        IconButton(onClick = { viewModel.startRemoteControl() }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "断开")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showControls && uiState.navBarPosition != "隐藏") {
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
                            Text("返回")
                        }
                        FilledTonalButton(onClick = { viewModel.sendKey(3) }) {
                            Icon(Icons.Filled.Home, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("主页")
                        }
                        FilledTonalButton(onClick = { viewModel.sendKey(187) }) {
                            Icon(Icons.AutoMirrored.Filled.ViewList, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("最近")
                        }
                        IconButton(onClick = { viewModel.sendKey(25) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, "音量-")
                        }
                        IconButton(onClick = { viewModel.sendKey(24) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, "音量+")
                        }
                        IconButton(onClick = { viewModel.sendKey(26) }) {
                            Icon(Icons.Filled.PowerSettingsNew, "电源")
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
                    contentDescription = "远程屏幕",
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
                    Text("正在获取画面...", color = Color.White)
                }
            }

            // FPS overlay
            if (!showControls) {
                Text(
                    text = "${uiState.fps} fps | 长按显示控制栏",
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("远程控制") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
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
                        label = "刷新率",
                        value = when (uiState.refreshInterval) {
                            500L -> "低 (2fps)"
                            200L -> "中 (5fps)"
                            100L -> "高 (10fps)"
                            50L -> "极高 (20fps)"
                            else -> "中 (5fps)"
                        },
                        options = listOf("低 (2fps)", "中 (5fps)", "高 (10fps)", "极高 (20fps)"),
                        onValueChange = { viewModel.setRefreshRate(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingRow(
                        label = "画面比例",
                        value = uiState.aspectRatio,
                        options = listOf("保持原始比例", "16:9", "4:3", "自适应"),
                        onValueChange = { viewModel.setAspectRatio(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingRow(
                        label = "导航栏位置",
                        value = uiState.navBarPosition,
                        options = listOf("悬浮", "底部", "隐藏"),
                        onValueChange = { viewModel.setNavBarPosition(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = "全屏显示",
                        checked = uiState.fullscreen,
                        onCheckedChange = { viewModel.setFullscreen(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = "熄屏控制",
                        checked = uiState.screenOff,
                        onCheckedChange = { viewModel.setScreenOff(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = "兼容模式",
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
                    Text("使用说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 点击屏幕画面即可操作远程设备", style = MaterialTheme.typography.bodySmall)
                    Text("• 滑动画面可模拟滑动操作", style = MaterialTheme.typography.bodySmall)
                    Text("• 长按画面可切换控制栏显示", style = MaterialTheme.typography.bodySmall)
                    Text("• 底部导航栏提供返回/主页/最近任务等快捷键", style = MaterialTheme.typography.bodySmall)
                    Text("• 刷新率越高越流畅，但消耗更多资源", style = MaterialTheme.typography.bodySmall)
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
                Text("连接", style = MaterialTheme.typography.bodyLarge)
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
