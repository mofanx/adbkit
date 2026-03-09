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
import com.adbkit.app.ui.strings.LocalStrings
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
    val strings = LocalStrings.current

    val tools = listOf(
        ToolItem(strings.toolScreenshot, Icons.Outlined.PhotoCamera, "screenshot", strings.toolDescScreenshot),
        ToolItem(strings.toolScreenRecord, Icons.Outlined.Videocam, "screenrecord", strings.toolDescScreenRecord),
        ToolItem(strings.toolInstallApk, Icons.Outlined.GetApp, "install", strings.toolDescInstallApk),
        ToolItem(strings.toolRebootDevice, Icons.Outlined.Refresh, "reboot", strings.toolDescRebootDevice),
        ToolItem(strings.toolInputText, Icons.Outlined.Keyboard, "input_text", strings.toolDescInputText),
        ToolItem(strings.toolKeyEvent, Icons.Outlined.Gesture, "key_event", strings.toolDescKeyEvent),
        ToolItem(strings.toolBrightness, Icons.Outlined.WbSunny, "brightness", strings.toolDescBrightness),
        ToolItem(strings.toolScreenTimeout, Icons.Outlined.Timer, "screen_timeout", strings.toolDescScreenTimeout),
        ToolItem(strings.toolWifiToggle, Icons.Outlined.Wifi, "wifi", strings.toolDescWifiToggle),
        ToolItem(strings.toolBluetoothToggle, Icons.Filled.Bluetooth, "bluetooth", strings.toolDescBluetoothToggle),
        ToolItem(strings.toolAirplaneMode, Icons.Outlined.AirplanemodeActive, "airplane", strings.toolDescAirplaneMode),
        ToolItem(strings.toolOpenUrl, Icons.Outlined.Link, "open_url", strings.toolDescOpenUrl),
        ToolItem(strings.toolLaunchApp, Icons.AutoMirrored.Outlined.OpenInNew, "launch_app", strings.toolDescLaunchApp),
        ToolItem(strings.toolCurrentActivity, Icons.Outlined.Layers, "current_activity", strings.toolDescCurrentActivity),
        ToolItem(strings.toolLogcat, Icons.Filled.BugReport, "logcat", strings.toolDescLogcat),
        ToolItem(strings.toolSystemProperties, Icons.Outlined.Settings, "sysprop", strings.toolDescSystemProperties),
        ToolItem(strings.toolScreenDensity, Icons.Outlined.Fullscreen, "density", strings.toolDescScreenDensity),
        ToolItem(strings.toolScreenResolution, Icons.Outlined.FitScreen, "resolution", strings.toolDescScreenResolution),
        ToolItem(strings.toolNavBar, Icons.Outlined.VerticalAlignBottom, "navbar", strings.toolDescNavBar),
        ToolItem(strings.toolStatusBar, Icons.Outlined.VerticalAlignTop, "statusbar", strings.toolDescStatusBar),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenTools) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
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
                        Text(LocalStrings.current.ok)
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
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.rebootDevice) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "" to strings.normalReboot,
                    "recovery" to strings.rebootToRecovery,
                    "bootloader" to strings.rebootToBootloader,
                    "fastboot" to strings.rebootToFastboot,
                    "edl" to strings.rebootToEdl
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
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.cancel) }
        }
    )
}

@Composable
fun InputTextDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.inputText) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(strings.textContent) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }) { Text(strings.send) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun KeyEventDialog(onDismiss: () -> Unit, onSend: (Int) -> Unit) {
    val strings = LocalStrings.current
    val keyEvents = listOf(
        3 to strings.keyHome, 4 to strings.keyBack, 24 to strings.keyVolUp, 25 to strings.keyVolDown,
        26 to strings.keyPower, 82 to strings.keyMenu, 187 to strings.keyRecent,
        164 to strings.keyMute, 223 to strings.keySleep, 224 to strings.keyWake,
        61 to strings.keyTab, 66 to strings.keyEnter, 67 to strings.keyBackspace,
        111 to strings.keyEsc, 120 to strings.keyScreenshot
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.keyEvent) },
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
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.close) }
        }
    )
}

@Composable
fun BrightnessDialog(onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    val strings = LocalStrings.current
    var brightness by remember { mutableFloatStateOf(128f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.screenBrightness) },
        text = {
            Column {
                Text(strings.brightnessValue(brightness.toInt()))
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..255f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(brightness.toInt()) }) { Text(strings.set) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun OpenUrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val strings = LocalStrings.current
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.openUrl) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onOpen(url) }) { Text(strings.ok) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun LaunchAppDialog(onDismiss: () -> Unit, onLaunch: (String) -> Unit) {
    val strings = LocalStrings.current
    var pkg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.launchApp) },
        text = {
            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it },
                label = { Text(strings.packageName) },
                placeholder = { Text("com.example.app") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onLaunch(pkg) }) { Text(strings.launch) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun DensityDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    val strings = LocalStrings.current
    var dpi by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.modifyDensity) },
        text = {
            Column {
                Text(strings.commonDpi,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dpi,
                    onValueChange = { dpi = it },
                    label = { Text(strings.dpiValue) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onSet("reset") }) {
                    Text(strings.resetDefault)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(dpi) }) { Text(strings.set) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun ResolutionDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    val strings = LocalStrings.current
    var resolution by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.modifyResolution) },
        text = {
            Column {
                Text(strings.resolutionFormat, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    label = { Text(strings.resolution) },
                    placeholder = { Text("1080x1920") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onSet("reset") }) {
                    Text(strings.resetDefault)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(resolution) }) { Text(strings.set) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
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
                        Icon(Icons.Filled.Refresh, contentDescription = LocalStrings.current.refresh)
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Delete, contentDescription = LocalStrings.current.clear)
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
                    text = output.ifEmpty { LocalStrings.current.noLog },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.close) }
        }
    )
}
