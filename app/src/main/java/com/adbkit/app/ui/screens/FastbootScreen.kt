package com.adbkit.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.ui.components.ConfirmDialog
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.FastbootViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastbootScreen(
    onMenuClick: () -> Unit,
    viewModel: FastbootViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(AdbKitApplication.instance) }
    val confirmDangerous by settingsRepo.confirmDangerous.collectAsState(initial = true)

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingMessage by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: "image_${System.currentTimeMillis()}"
            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(it)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.setImagePath(cacheFile.absolutePath)
        }
    }

    fun runDangerous(title: String, message: String, action: () -> Unit) {
        if (confirmDangerous) {
            pendingTitle = title
            pendingMessage = message
            pendingAction = action
        } else {
            action()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenFastboot) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.detectDevice)
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
                        text = if (uiState.deviceDetected) strings.fastbootDeviceConnected
                        else when (uiState.permissionWarning) {
                            "fastboot_no_permission" -> strings.fastbootNoPermission
                            "fastboot_no_device" -> strings.fastbootNoDevice
                            else -> strings.fastbootInsertDevice
                        },
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
                        strings.flashImage,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Image path
                    OutlinedTextField(
                        value = uiState.imagePath,
                        onValueChange = { viewModel.setImagePath(it) },
                        label = { Text(strings.imageFilePath) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { imagePickerLauncher.launch("*/*") }) {
                                Icon(Icons.Filled.Folder, strings.selectFile,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.expectedMd5,
                        onValueChange = { viewModel.setExpectedMd5(it) },
                        label = { Text(strings.expectedMd5) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Partition selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${strings.partition}:", style = MaterialTheme.typography.bodyMedium)

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

                        OutlinedButton(
                            onClick = { viewModel.verifyImageMd5() },
                            enabled = uiState.imagePath.isNotBlank() && !uiState.isExecuting
                        ) {
                            if (uiState.isExecuting && uiState.imageMd5.isEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(strings.verifyMd5)
                        }

                        Button(
                            onClick = {
                                runDangerous(
                                    strings.flashImage,
                                    "Flash ${uiState.selectedPartition} with ${uiState.imagePath.substringAfterLast('/')}? This may brick the device.",
                                    { viewModel.flashImage() }
                                )
                            },
                            enabled = uiState.imagePath.isNotBlank()
                        ) {
                            Text(strings.flash)
                        }
                    }

                    if (uiState.imageMd5.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.md5Result.format(uiState.imageMd5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                        strings.quickActions,
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
                        ) { Text(strings.rebootSystem) }
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("bootloader") },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.rebootBl) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("recovery") },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.rebootRecovery) }
                        OutlinedButton(
                            onClick = { viewModel.fastbootReboot("fastboot") },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.rebootFastbootd) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { runDangerous(strings.unlockBl, "Unlocking the bootloader will erase all data. Continue?", { viewModel.unlockBootloader() }) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(strings.unlockBl) }
                        OutlinedButton(
                            onClick = { runDangerous(strings.lockBl, "Locking the bootloader may prevent future modifications. Continue?", { viewModel.lockBootloader() }) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(strings.lockBl) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { runDangerous(strings.erasePartition, "Erase ${uiState.selectedPartition}? All data on that partition will be lost.", { viewModel.erasePartition() }) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(strings.erasePartition) }
                        OutlinedButton(
                            onClick = { viewModel.getVar() },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.deviceVariables) }
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
                        strings.customCommand,
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
                            Text(strings.execute)
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
                            strings.commandOutput,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { viewModel.saveFlashLog() }) {
                                Icon(Icons.Filled.Save, strings.saveLog)
                            }
                            IconButton(onClick = { viewModel.clearOutput() }) {
                                Icon(Icons.Filled.DeleteSweep, strings.clear)
                            }
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

            // Dangerous action confirmation
            if (pendingAction != null) {
                ConfirmDialog(
                    title = pendingTitle,
                    message = pendingMessage,
                    confirmText = strings.confirm,
                    dismissText = strings.cancel,
                    isDestructive = true,
                    onConfirm = {
                        pendingAction?.invoke()
                        pendingAction = null
                    },
                    onDismiss = { pendingAction = null }
                )
            }
        }
    }
}
