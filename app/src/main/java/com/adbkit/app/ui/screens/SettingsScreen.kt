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
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenSettings) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ADB Configuration
            SettingsSection(title = strings.adbConfig) {
                // ADB ready status indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.adbReady)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.adbReady) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (uiState.adbReady)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.adbReady) strings.adbStatusReady else strings.adbStatusNotReady,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.adbReady)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Path: ${com.adbkit.app.service.AdbService.getAdbPath()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.adbReady)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.adbPath,
                    onValueChange = { viewModel.setAdbPath(it) },
                    label = { Text(strings.adbPath) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(strings.adbPathHint) },
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
                        Text(strings.detect)
                    }
                    OutlinedButton(
                        onClick = { viewModel.autoDetectAdb() },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isCheckingAdb
                    ) {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.autoDetect)
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

                // Diagnostics button
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { viewModel.showDiagnostics() }) {
                    Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.adbDiagnostics)
                }
                if (uiState.adbDiagnostics.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = uiState.adbDiagnostics,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.fastbootPath,
                    onValueChange = { viewModel.setFastbootPath(it) },
                    label = { Text(strings.fastbootPath) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.defaultPort,
                    onValueChange = { viewModel.setDefaultPort(it) },
                    label = { Text(strings.defaultPort) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Connection settings
            SettingsSection(title = strings.connectionSettings) {
                SettingsSwitch(
                    label = strings.autoConnectLastDevice,
                    checked = uiState.autoConnect,
                    onCheckedChange = { viewModel.setAutoConnect(it) }
                )
                SettingsSwitch(
                    label = strings.keepScreenOn,
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
                SettingsSwitch(
                    label = strings.saveCommandHistory,
                    checked = uiState.saveHistory,
                    onCheckedChange = { viewModel.setSaveHistory(it) }
                )
            }

            // Safety settings
            SettingsSection(title = strings.safetySettings) {
                SettingsSwitch(
                    label = strings.confirmDangerous,
                    description = strings.confirmDangerousDesc,
                    checked = uiState.confirmDangerous,
                    onCheckedChange = { viewModel.setConfirmDangerous(it) }
                )
            }

            // Appearance settings
            SettingsSection(title = strings.appearanceSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.darkMode, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.darkMode == "system",
                            onClick = { viewModel.setDarkMode("system") },
                            label = { Text(strings.darkModeSystem) }
                        )
                        FilterChip(
                            selected = uiState.darkMode == "light",
                            onClick = { viewModel.setDarkMode("light") },
                            label = { Text(strings.darkModeLight) }
                        )
                        FilterChip(
                            selected = uiState.darkMode == "dark",
                            onClick = { viewModel.setDarkMode("dark") },
                            label = { Text(strings.darkModeDark) }
                        )
                    }
                }
                SettingsSwitch(
                    label = strings.dynamicColor,
                    description = strings.dynamicColorDesc,
                    checked = uiState.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            // Language settings
            SettingsSection(title = strings.languageSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.language, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.language == "zh",
                            onClick = { viewModel.setLanguage("zh") },
                            label = { Text(strings.chinese) }
                        )
                        FilterChip(
                            selected = uiState.language == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text(strings.english) }
                        )
                    }
                }
            }

            // About section
            SettingsSection(title = strings.about) {
                SettingsInfoRow(strings.aboutAppName, "ADB Kit")
                SettingsInfoRow(strings.aboutVersion, "1.1.0")
                SettingsInfoRow(strings.aboutDeveloper, "ADB Kit Team")
                SettingsInfoRow(strings.aboutRepo, "github.com/mofanx/adbkit")
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
