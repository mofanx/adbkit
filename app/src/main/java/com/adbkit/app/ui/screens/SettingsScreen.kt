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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onMenuClick: () -> Unit) {
    var adbPath by remember { mutableStateOf("adb") }
    var fastbootPath by remember { mutableStateOf("fastboot") }
    var defaultPort by remember { mutableStateOf("5555") }
    var autoConnect by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(false) }
    var dynamicColor by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(false) }
    var confirmDangerous by remember { mutableStateOf(true) }
    var saveHistory by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ADB Configuration
            SettingsSection(title = "ADB 配置") {
                OutlinedTextField(
                    value = adbPath,
                    onValueChange = { adbPath = it },
                    label = { Text("ADB 路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fastbootPath,
                    onValueChange = { fastbootPath = it },
                    label = { Text("Fastboot 路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultPort,
                    onValueChange = { defaultPort = it },
                    label = { Text("默认端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Connection settings
            SettingsSection(title = "连接设置") {
                SettingsSwitch(
                    label = "自动连接上次设备",
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it }
                )
                SettingsSwitch(
                    label = "保持屏幕常亮",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it }
                )
                SettingsSwitch(
                    label = "保存命令历史",
                    checked = saveHistory,
                    onCheckedChange = { saveHistory = it }
                )
            }

            // Safety settings
            SettingsSection(title = "安全设置") {
                SettingsSwitch(
                    label = "危险操作确认",
                    description = "卸载、重启、擦除等操作前需要确认",
                    checked = confirmDangerous,
                    onCheckedChange = { confirmDangerous = it }
                )
            }

            // Appearance settings
            SettingsSection(title = "外观设置") {
                SettingsSwitch(
                    label = "深色模式",
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )
                SettingsSwitch(
                    label = "动态颜色 (Material You)",
                    description = "Android 12+ 根据壁纸生成主题色",
                    checked = dynamicColor,
                    onCheckedChange = { dynamicColor = it }
                )
            }

            // About section
            SettingsSection(title = "关于") {
                SettingsInfoRow("应用名称", "ADB Kit")
                SettingsInfoRow("版本", "1.0.0")
                SettingsInfoRow("开发者", "ADB Kit Team")
                SettingsInfoRow("项目地址", "github.com/mofanx/adbkit")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
