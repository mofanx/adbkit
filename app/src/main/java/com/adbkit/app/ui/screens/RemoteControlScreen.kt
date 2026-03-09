package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.adbkit.app.ui.viewmodel.RemoteControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onMenuClick: () -> Unit,
    viewModel: RemoteControlViewModel = viewModel()
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
            // Settings section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Resolution
                    SettingRow(
                        label = "分辨率",
                        value = uiState.resolution,
                        options = listOf("自动调整", "1920x1080", "1280x720", "960x540", "640x480"),
                        onValueChange = { viewModel.setResolution(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Bitrate
                    SettingRow(
                        label = "码率",
                        value = uiState.bitrate,
                        options = listOf("2Mbps", "4Mbps", "8Mbps", "16Mbps", "32Mbps"),
                        onValueChange = { viewModel.setBitrate(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Aspect ratio
                    SettingRow(
                        label = "画面比例",
                        value = uiState.aspectRatio,
                        options = listOf("保持原始比例", "16:9", "4:3", "自适应"),
                        onValueChange = { viewModel.setAspectRatio(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Nav bar position
                    SettingRow(
                        label = "导航栏位置",
                        value = uiState.navBarPosition,
                        options = listOf("悬浮", "底部", "隐藏"),
                        onValueChange = { viewModel.setNavBarPosition(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Fullscreen toggle
                    ToggleRow(
                        label = "全屏显示",
                        checked = uiState.fullscreen,
                        onCheckedChange = { viewModel.setFullscreen(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Screen off control
                    ToggleRow(
                        label = "熄屏控制",
                        checked = uiState.screenOff,
                        onCheckedChange = { viewModel.setScreenOff(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Compat mode
                    ToggleRow(
                        label = "兼容模式",
                        checked = uiState.compatMode,
                        onCheckedChange = { viewModel.setCompatMode(it) }
                    )
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
                Text(if (uiState.isConnected) "断开连接" else "连接")
            }

            // Virtual controls section
            if (uiState.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "虚拟按键",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Direction pad
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.width(56.dp))
                            FilledTonalIconButton(onClick = { viewModel.sendSwipe("up") }) {
                                Icon(Icons.Filled.KeyboardArrowUp, "上")
                            }
                            Spacer(modifier = Modifier.width(56.dp))
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalIconButton(onClick = { viewModel.sendSwipe("left") }) {
                                Icon(Icons.Filled.KeyboardArrowLeft, "左")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalIconButton(onClick = { viewModel.sendTap() }) {
                                Icon(Icons.Filled.FiberManualRecord, "点击")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalIconButton(onClick = { viewModel.sendSwipe("right") }) {
                                Icon(Icons.Filled.KeyboardArrowRight, "右")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.width(56.dp))
                            FilledTonalIconButton(onClick = { viewModel.sendSwipe("down") }) {
                                Icon(Icons.Filled.KeyboardArrowDown, "下")
                            }
                            Spacer(modifier = Modifier.width(56.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Navigation buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilledTonalButton(onClick = { viewModel.sendKey(4) }) {
                                Icon(Icons.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回")
                            }
                            FilledTonalButton(onClick = { viewModel.sendKey(3) }) {
                                Icon(Icons.Filled.Home, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("主页")
                            }
                            FilledTonalButton(onClick = { viewModel.sendKey(187) }) {
                                Icon(Icons.Filled.ViewList, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("最近")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Volume & Power
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(onClick = { viewModel.sendKey(25) }) {
                                Text("音量-")
                            }
                            OutlinedButton(onClick = { viewModel.sendKey(24) }) {
                                Text("音量+")
                            }
                            OutlinedButton(onClick = { viewModel.sendKey(26) }) {
                                Text("电源")
                            }
                        }
                    }
                }
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
