package com.adbkit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.TerminalViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onMenuClick: () -> Unit,
    viewModel: TerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom
    LaunchedEffect(uiState.outputLines.size) {
        if (uiState.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.outputLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenTerminal) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearOutput() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = strings.clearScreen)
                    }
                    IconButton(onClick = { viewModel.toggleShellMode() }) {
                        Icon(
                            if (uiState.isShellMode) Icons.Filled.Code else Icons.Filled.Build,
                            contentDescription = strings.switchMode
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode indicator
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (uiState.isShellMode) strings.adbShellMode else strings.adbCommandMode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = uiState.currentDevice ?: strings.notConnected,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Quick commands
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val quickCommands = if (uiState.isShellMode) {
                    listOf("ls", "pwd", "ps", "df", "top -n 1", "getprop")
                } else {
                    listOf("devices", "version", "get-state", "get-serialno", "bugreport")
                }
                quickCommands.forEach { cmd ->
                    AssistChip(
                        onClick = { viewModel.executeQuickCommand(cmd) },
                        label = { Text(cmd, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Output area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp),
                state = listState
            ) {
                items(uiState.outputLines) { line ->
                    Text(
                        text = line,
                        color = when {
                            line.startsWith("$ ") || line.startsWith(">>> ") -> Color(0xFF4EC9B0)
                            line.startsWith("ERR:") || line.startsWith("Error:") -> Color(0xFFFF6B6B)
                            line.startsWith("---") -> Color(0xFF569CD6)
                            else -> Color(0xFFD4D4D4)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Command history
            if (uiState.showHistory && uiState.commandHistory.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    LazyColumn(modifier = Modifier.padding(4.dp)) {
                        items(uiState.commandHistory.reversed()) { cmd ->
                            TextButton(
                                onClick = { viewModel.setCommand(cmd) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isShellMode) "$ " else "adb ",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = uiState.currentCommand,
                        onValueChange = { viewModel.setCommand(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(strings.enterCommand) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(Icons.Filled.History, contentDescription = strings.history)
                    }
                    FilledIconButton(
                        onClick = { viewModel.executeCommand() },
                        enabled = uiState.currentCommand.isNotBlank() && !uiState.isExecuting
                    ) {
                        if (uiState.isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.execute)
                        }
                    }
                }
            }
        }
    }
}
