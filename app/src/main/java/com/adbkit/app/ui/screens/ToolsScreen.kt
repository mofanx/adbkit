package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.viewmodel.ToolsViewModel

data class ToolItem(
    val name: String,
    val icon: ImageVector,
    val action: String,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onMenuClick: () -> Unit,
    viewModel: ToolsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val tools = listOf(
        ToolItem("截图", Icons.Outlined.PhotoCamera, "screenshot", "截取设备屏幕"),
        ToolItem("录屏", Icons.Outlined.Videocam, "screenrecord", "录制设备屏幕"),
        ToolItem("安装APK", Icons.Outlined.GetApp, "install", "安装应用程序"),
        ToolItem("重启设备", Icons.Outlined.Refresh, "reboot", "重启到不同模式"),
        ToolItem("输入文本", Icons.Outlined.Keyboard, "input_text", "向设备输入文字"),
        ToolItem("按键模拟", Icons.Outlined.Gesture, "key_event", "模拟按键操作"),
        ToolItem("屏幕亮度", Icons.Outlined.WbSunny, "brightness", "调节屏幕亮度"),
        ToolItem("屏幕超时", Icons.Outlined.Timer, "screen_timeout", "设置屏幕自动关闭时间"),
        ToolItem("WiFi开关", Icons.Outlined.Wifi, "wifi", "开关WiFi"),
        ToolItem("蓝牙开关", Icons.Filled.Bluetooth, "bluetooth", "开关蓝牙"),
        ToolItem("飞行模式", Icons.Outlined.AirplanemodeActive, "airplane", "开关飞行模式"),
        ToolItem("打开链接", Icons.Outlined.Link, "open_url", "在设备上打开URL"),
        ToolItem("启动应用", Icons.AutoMirrored.Outlined.OpenInNew, "launch_app", "启动指定应用"),
        ToolItem("当前Activity", Icons.Outlined.Layers, "current_activity", "查看当前Activity"),
        ToolItem("Logcat", Icons.Filled.BugReport, "logcat", "查看系统日志"),
        ToolItem("系统属性", Icons.Outlined.Settings, "sysprop", "查看/修改系统属性"),
        ToolItem("屏幕密度", Icons.Outlined.Fullscreen, "density", "修改屏幕密度DPI"),
        ToolItem("屏幕分辨率", Icons.Outlined.FitScreen, "resolution", "修改屏幕分辨率"),
        ToolItem("导航栏", Icons.Outlined.VerticalAlignBottom, "navbar", "显示/隐藏导航栏"),
        ToolItem("状态栏", Icons.Outlined.VerticalAlignTop, "statusbar", "显示/隐藏状态栏"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实用工具") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tools) { tool ->
                ToolCard(
                    tool = tool,
                    onClick = { viewModel.executeTool(tool.action) }
                )
            }
        }

        // Dialogs
        when (uiState.activeDialog) {
            "reboot" -> RebootDialog(
                onDismiss = { viewModel.dismissDialog() },
                onReboot = { mode -> viewModel.reboot(mode) }
            )
            "input_text" -> InputTextDialog(
                onDismiss = { viewModel.dismissDialog() },
                onSend = { text -> viewModel.inputText(text) }
            )
            "key_event" -> KeyEventDialog(
                onDismiss = { viewModel.dismissDialog() },
                onSend = { code -> viewModel.sendKeyEvent(code) }
            )
            "brightness" -> BrightnessDialog(
                onDismiss = { viewModel.dismissDialog() },
                onSet = { value -> viewModel.setBrightness(value) }
            )
            "open_url" -> OpenUrlDialog(
                onDismiss = { viewModel.dismissDialog() },
                onOpen = { url -> viewModel.openUrl(url) }
            )
            "launch_app" -> LaunchAppDialog(
                onDismiss = { viewModel.dismissDialog() },
                onLaunch = { pkg -> viewModel.launchApp(pkg) }
            )
            "density" -> DensityDialog(
                onDismiss = { viewModel.dismissDialog() },
                onSet = { dpi -> viewModel.setDensity(dpi) }
            )
            "resolution" -> ResolutionDialog(
                onDismiss = { viewModel.dismissDialog() },
                onSet = { res -> viewModel.setResolution(res) }
            )
            "logcat_view" -> LogcatViewDialog(
                output = uiState.commandOutput,
                onDismiss = { viewModel.dismissDialog() },
                onRefresh = { viewModel.executeTool("logcat") },
                onClear = { viewModel.clearLogcat() }
            )
        }

        // Result snackbar
        if (uiState.statusMessage.isNotEmpty()) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearStatus() }) {
                        Text("确定")
                    }
                }
            ) {
                Text(uiState.statusMessage)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.name,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RebootDialog(onDismiss: () -> Unit, onReboot: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重启设备") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "" to "正常重启",
                    "recovery" to "重启到Recovery",
                    "bootloader" to "重启到Bootloader",
                    "fastboot" to "重启到Fastboot",
                    "edl" to "重启到EDL(9008)"
                ).forEach { (mode, label) ->
                    TextButton(
                        onClick = { onReboot(mode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun InputTextDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入文本") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("文本内容") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }) { Text("发送") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun KeyEventDialog(onDismiss: () -> Unit, onSend: (Int) -> Unit) {
    val keyEvents = listOf(
        3 to "Home", 4 to "Back", 24 to "音量+", 25 to "音量-",
        26 to "电源键", 82 to "菜单", 187 to "最近任务",
        164 to "静音", 223 to "休眠", 224 to "唤醒",
        61 to "Tab", 66 to "Enter", 67 to "退格",
        111 to "Esc", 120 to "截图"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按键模拟") },
        text = {
            Column {
                keyEvents.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { (code, label) ->
                            OutlinedButton(
                                onClick = { onSend(code) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        // Fill remaining space
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun BrightnessDialog(onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var brightness by remember { mutableFloatStateOf(128f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("屏幕亮度") },
        text = {
            Column {
                Text("亮度值: ${brightness.toInt()}")
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..255f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(brightness.toInt()) }) { Text("设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun OpenUrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打开链接") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onOpen(url) }) { Text("打开") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun LaunchAppDialog(onDismiss: () -> Unit, onLaunch: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("启动应用") },
        text = {
            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it },
                label = { Text("包名") },
                placeholder = { Text("com.example.app") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onLaunch(pkg) }) { Text("启动") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DensityDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var dpi by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改屏幕密度") },
        text = {
            Column {
                Text("常见DPI: 120(ldpi), 160(mdpi), 240(hdpi), 320(xhdpi), 480(xxhdpi), 640(xxxhdpi)",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dpi,
                    onValueChange = { dpi = it },
                    label = { Text("DPI值") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onSet("reset") }) {
                    Text("恢复默认")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(dpi) }) { Text("设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ResolutionDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var resolution by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改屏幕分辨率") },
        text = {
            Column {
                Text("格式: 宽x高 (例如: 1080x1920)", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    label = { Text("分辨率") },
                    placeholder = { Text("1080x1920") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onSet("reset") }) {
                    Text("恢复默认")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(resolution) }) { Text("设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun LogcatViewDialog(
    output: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Logcat")
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Delete, contentDescription = "清除")
                    }
                }
            }
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = output.ifEmpty { "无日志" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
