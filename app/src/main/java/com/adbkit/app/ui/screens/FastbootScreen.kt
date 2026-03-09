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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.viewmodel.FastbootViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastbootScreen(
    onMenuClick: () -> Unit,
    viewModel: FastbootViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fastboot") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "检测设备")
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
            // Device status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.deviceDetected)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
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
                        if (uiState.deviceDetected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (uiState.deviceDetected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.deviceDetected) "Fastboot 设备已连接" else "请插入设备",
                        color = if (uiState.deviceDetected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Flash image section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "刷入镜像",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Image path
                    OutlinedTextField(
                        value = uiState.imagePath,
                        onValueChange = { viewModel.setImagePath(it) },
                        label = { Text("镜像文件路径") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { /* File picker */ }) {
                                Icon(Icons.Filled.Folder, "选择文件",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Partition selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("分区:", style = MaterialTheme.typography.bodyMedium)

                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(uiState.selectedPartition)
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf(
                                    "recovery", "boot", "system", "vendor", "dtbo",
                                    "vbmeta", "cache", "userdata", "super"
                                ).forEach { partition ->
                                    DropdownMenuItem(
                                        text = { Text(partition) },
                                        onClick = {
                                            viewModel.setPartition(partition)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.flashImage() },
                            enabled = uiState.imagePath.isNotBlank()
                        ) {
                            Text("刷入")
                        }
                    }
                }
            }

            // Quick actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "快捷操作",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("") },
                            modifier = Modifier.weight(1f)
                        ) { Text("重启系统") }
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("bootloader") },
                            modifier = Modifier.weight(1f)
                        ) { Text("重启BL") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("recovery") },
                            modifier = Modifier.weight(1f)
                        ) { Text("重启Recovery") }
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("fastboot") },
                            modifier = Modifier.weight(1f)
                        ) { Text("重启Fastbootd") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.unlockBootloader() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("解锁BL") }
                        OutlinedButton(
                            onClick = { viewModel.lockBootloader() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("锁定BL") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.erasePartition() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("擦除分区") }
                        OutlinedButton(
                            onClick = { viewModel.getVar() },
                            modifier = Modifier.weight(1f)
                        ) { Text("设备变量") }
                    }
                }
            }

            // Custom command section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "自定义命令",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.customCommand,
                            onValueChange = { viewModel.setCustomCommand(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("fastboot ...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.executeCustomCommand() },
                            enabled = uiState.customCommand.isNotBlank()
                        ) {
                            Text("执行")
                        }
                    }
                }
            }

            // Output section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "命令行输出",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearOutput() }) {
                            Icon(Icons.Filled.DeleteSweep, "清除")
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 300.dp),
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = uiState.output.ifEmpty { "$ " },
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
