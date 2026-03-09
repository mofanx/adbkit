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
import com.adbkit.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    value = uiState.adbPath,
                    onValueChange = { viewModel.setAdbPath(it) },
                    label = { Text("ADB 路径") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("默认 \"adb\"，可指定完整路径如 /data/local/tmp/adb") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.checkAdbAvailability() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isCheckingAdb
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("检测")
                    }
                    OutlinedButton(
                        onClick = { viewModel.autoDetectAdb() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isCheckingAdb
                    ) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("自动查找")
                    }
                }
                if (uiState.adbStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.adbStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.adbStatus.startsWith("✓"))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.fastbootPath,
                    onValueChange = { viewModel.setFastbootPath(it) },
                    label = { Text("Fastboot 路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.defaultPort,
                    onValueChange = { viewModel.setDefaultPort(it) },
                    label = { Text("默认端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Connection settings
            SettingsSection(title = "连接设置") {
                SettingsSwitch(
                    label = "自动连接上次设备",
                    checked = uiState.autoConnect,
                    onCheckedChange = { viewModel.setAutoConnect(it) }
                )
                SettingsSwitch(
                    label = "保持屏幕常亮",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
                SettingsSwitch(
                    label = "保存命令历史",
                    checked = uiState.saveHistory,
                    onCheckedChange = { viewModel.setSaveHistory(it) }
                )
            }

            // Safety settings
            SettingsSection(title = "安全设置") {
                SettingsSwitch(
                    label = "危险操作确认",
                    description = "卸载、重启、擦除等操作前需要确认",
                    checked = uiState.confirmDangerous,
                    onCheckedChange = { viewModel.setConfirmDangerous(it) }
                )
            }

            // Appearance settings
            SettingsSection(title = "外观设置") {
                SettingsSwitch(
                    label = "深色模式",
                    checked = uiState.darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
                SettingsSwitch(
                    label = "动态颜色 (Material You)",
                    description = "Android 12+ 根据壁纸生成主题色",
                    checked = uiState.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            // ADB Integration Guide
            SettingsSection(title = "ADB 集成说明") {
                Text(
                    text = "本应用需要可用的 adb 二进制文件。Android 系统默认不包含 adb，您需要通过以下方式之一提供：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "方案一：使用 Termux 安装 adb",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "1. 安装 Termux\n2. 执行: pkg install android-tools\n3. ADB 路径设为: adb (Termux环境下直接可用)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "方案二：手动放置 adb",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "1. 从 Android SDK Platform-Tools 下载对应架构的 adb\n" +
                            "2. 将 adb 复制到 /data/local/tmp/adb\n" +
                            "3. 执行: chmod +x /data/local/tmp/adb\n" +
                            "4. 在上方设置路径为: /data/local/tmp/adb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "方案三：Root 设备",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Root 后部分 ROM 自带 adb，路径通常为 /system/bin/adb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
