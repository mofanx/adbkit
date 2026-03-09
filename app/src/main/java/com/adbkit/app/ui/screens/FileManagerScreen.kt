package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.FileManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onMenuClick: () -> Unit,
    viewModel: FileManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenFileManager) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                    }
                    IconButton(onClick = { viewModel.showCreateDirDialog() }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = strings.newFolder)
                    }
                    IconButton(onClick = { viewModel.navigateToHome() }) {
                        Icon(Icons.Filled.Home, contentDescription = strings.homeDir)
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
            // Path bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUp() },
                        enabled = uiState.currentPath != "/"
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = strings.parentDir)
                    }
                    Text(
                        text = uiState.currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // Quick navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "/sdcard" to strings.sdCard,
                    "/sdcard/Download" to strings.download,
                    "/sdcard/DCIM" to strings.album,
                    "/data" to strings.data,
                    "/" to strings.rootDir
                ).forEach { (path, label) ->
                    AssistChip(
                        onClick = { viewModel.navigateTo(path) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ErrorOutline, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.error, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(uiState.files) { file ->
                        FileItemRow(
                            file = file,
                            onClick = {
                                if (file["isDirectory"] == "true") {
                                    viewModel.navigateTo(file["path"] ?: "")
                                }
                            },
                            onDelete = { viewModel.deleteFile(file["path"] ?: "") },
                            onPull = { viewModel.pullFile(file["path"] ?: "", file["name"] ?: "") }
                        )
                    }

                    if (uiState.files.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(strings.emptyDir, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Create directory dialog
        if (uiState.showCreateDirDialog) {
            var dirName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.hideCreateDirDialog() },
                title = { Text(strings.newFolder) },
                text = {
                    OutlinedTextField(
                        value = dirName,
                        onValueChange = { dirName = it },
                        label = { Text(strings.folderName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.createDirectory(dirName) }) { Text(strings.create) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideCreateDirDialog() }) { Text(strings.cancel) }
                }
            )
        }
    }
}

@Composable
fun FileItemRow(
    file: Map<String, String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPull: () -> Unit
) {
    val isDir = file["isDirectory"] == "true"
    val name = file["name"] ?: ""
    val size = file["size"] ?: ""
    val date = file["date"] ?: ""
    val permissions = file["permissions"] ?: ""

    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isDir && size.isNotEmpty()) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (date.isNotEmpty()) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = LocalStrings.current.more)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!isDir) {
                        DropdownMenuItem(
                            text = { Text(LocalStrings.current.downloadToLocal) },
                            onClick = {
                                showMenu = false
                                onPull()
                            },
                            leadingIcon = { Icon(Icons.Filled.Download, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.delete) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}
