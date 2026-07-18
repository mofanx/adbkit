package com.adbkit.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.ui.components.ConfirmDialog
import com.adbkit.app.ui.components.EmptyDevicePlaceholder
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.FileManagerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onMenuClick: () -> Unit,
    viewModel: FileManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(AdbKitApplication.instance) }
    val confirmDangerous by settingsRepo.confirmDangerous.collectAsState(initial = true)

    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingBatchDelete by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var pendingMove by remember { mutableStateOf<String?>(null) }
    var pendingCopy by remember { mutableStateOf<String?>(null) }

    // File picker for upload
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy URI content to cache file, then push
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: "upload_${System.currentTimeMillis()}"

            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(it)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.pushFile(cacheFile.absolutePath, fileName)
        }
    }

    // Trigger file picker when requested
    LaunchedEffect(uiState.requestFilePick) {
        if (uiState.requestFilePick) {
            viewModel.onFilePickHandled()
            filePickerLauncher.launch("*/*")
        }
    }

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
                    if (uiState.isSelectionMode) {
                        Text(
                            text = "${uiState.selectedFiles.size}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            if (confirmDangerous) {
                                pendingBatchDelete = true
                            } else {
                                viewModel.batchDelete()
                            }
                        }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Batch delete", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                        }
                        IconButton(onClick = { viewModel.showCreateDirDialog() }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = strings.newFolder)
                        }
                        IconButton(onClick = { viewModel.navigateToHome() }) {
                            Icon(Icons.Filled.Home, contentDescription = strings.homeDir)
                        }
                        // Upload button - triggers file picker
                        IconButton(onClick = { viewModel.requestUpload() }) {
                            Icon(Icons.Filled.Upload, contentDescription = strings.upload)
                        }
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

            // Root warning for protected directories
            val isProtectedPath = listOf("/", "/data", "/system", "/vendor", "/product", "/sbin").any { uiState.currentPath == it || uiState.currentPath.startsWith("$it/") }
            if (isProtectedPath && !uiState.hasRootAccess) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.rootRequiredMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Transfer status bar
            if (uiState.isTransferring || uiState.statusMessage.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (uiState.statusMessage.contains("failed", ignoreCase = true))
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isTransferring) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                EmptyDevicePlaceholder(
                    onRetry = { viewModel.refresh() },
                    message = uiState.error
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(uiState.files) { file ->
                        val path = file["path"] ?: ""
                        FileItemRow(
                            file = file,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedFiles.contains(path),
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleFileSelection(path)
                                } else if (file["isDirectory"] == "true") {
                                    viewModel.navigateTo(path)
                                }
                            },
                            onLongClick = { viewModel.enterSelectionMode() },
                            onSelect = { viewModel.toggleFileSelection(path) },
                            onDelete = {
                                if (confirmDangerous) {
                                    pendingDelete = path
                                } else {
                                    viewModel.deleteFile(path)
                                }
                            },
                            onPull = { viewModel.pullFile(path, file["name"] ?: "") },
                            onRename = { pendingRename = path },
                            onMove = { pendingMove = path },
                            onCopy = { pendingCopy = path },
                            onPreview = { viewModel.showPreview(path, file["name"] ?: "") }
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

        // Delete confirmation dialog
        if (pendingDelete != null) {
            ConfirmDialog(
                title = strings.delete,
                message = "Delete ${pendingDelete?.substringAfterLast('/')}? This cannot be undone.",
                confirmText = strings.delete,
                dismissText = strings.cancel,
                isDestructive = true,
                onConfirm = {
                    pendingDelete?.let { viewModel.deleteFile(it) }
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null }
            )
        }

        // Batch delete confirmation dialog
        if (pendingBatchDelete) {
            ConfirmDialog(
                title = strings.delete,
                message = "Delete ${uiState.selectedFiles.size} selected item(s)? This cannot be undone.",
                confirmText = strings.delete,
                dismissText = strings.cancel,
                isDestructive = true,
                onConfirm = {
                    viewModel.batchDelete()
                    pendingBatchDelete = false
                },
                onDismiss = { pendingBatchDelete = false }
            )
        }

        // Rename dialog
        if (pendingRename != null) {
            var newName by remember { mutableStateOf(pendingRename?.substringAfterLast('/') ?: "") }
            AlertDialog(
                onDismissRequest = { pendingRename = null },
                title = { Text(strings.rename) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(strings.newName) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRename?.let { viewModel.renameFile(it, newName) }
                        pendingRename = null
                    }) { Text(strings.ok) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRename = null }) { Text(strings.cancel) }
                }
            )
        }

        // Move dialog
        if (pendingMove != null) {
            var target by remember { mutableStateOf(pendingMove ?: "") }
            AlertDialog(
                onDismissRequest = { pendingMove = null },
                title = { Text(strings.move) },
                text = {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text(strings.targetPath) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingMove?.let { viewModel.moveFile(it, target) }
                        pendingMove = null
                    }) { Text(strings.ok) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingMove = null }) { Text(strings.cancel) }
                }
            )
        }

        // Copy dialog
        if (pendingCopy != null) {
            var target by remember { mutableStateOf((pendingCopy ?: "") + "_copy") }
            AlertDialog(
                onDismissRequest = { pendingCopy = null },
                title = { Text(strings.copy) },
                text = {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text(strings.targetPath) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingCopy?.let { viewModel.copyFile(it, target) }
                        pendingCopy = null
                    }) { Text(strings.ok) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCopy = null }) { Text(strings.cancel) }
                }
            )
        }

        // File preview dialog
        uiState.preview?.let { preview ->
            PreviewFileDialog(
                preview = preview,
                onDismiss = { viewModel.dismissPreview() }
            )
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
private fun getFileIcon(name: String, isDir: Boolean): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    val primary = MaterialTheme.colorScheme.primary
    val imageColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
    val videoColor = androidx.compose.ui.graphics.Color(0xFFF44336)
    val audioColor = androidx.compose.ui.graphics.Color(0xFF9C27B0)
    val codeColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
    val textColor = androidx.compose.ui.graphics.Color(0xFF607D8B)
    val archiveColor = androidx.compose.ui.graphics.Color(0xFFFF9800)
    val apkColor = androidx.compose.ui.graphics.Color(0xFF3DDC84)
    val default = MaterialTheme.colorScheme.onSurfaceVariant

    if (isDir) return Icons.Filled.Folder to primary

    return when {
        name.endsWith(".apk", ignoreCase = true) -> Icons.Filled.Android to apkColor
        name.endsWith(".jpg", ignoreCase = true) ||
            name.endsWith(".jpeg", ignoreCase = true) ||
            name.endsWith(".png", ignoreCase = true) ||
            name.endsWith(".gif", ignoreCase = true) ||
            name.endsWith(".webp", ignoreCase = true) -> Icons.Filled.Image to imageColor
        name.endsWith(".mp4", ignoreCase = true) ||
            name.endsWith(".mkv", ignoreCase = true) ||
            name.endsWith(".avi", ignoreCase = true) ||
            name.endsWith(".mov", ignoreCase = true) -> Icons.Filled.Videocam to videoColor
        name.endsWith(".mp3", ignoreCase = true) ||
            name.endsWith(".wav", ignoreCase = true) ||
            name.endsWith(".flac", ignoreCase = true) ||
            name.endsWith(".aac", ignoreCase = true) -> Icons.Filled.Audiotrack to audioColor
        name.endsWith(".zip", ignoreCase = true) ||
            name.endsWith(".rar", ignoreCase = true) ||
            name.endsWith(".7z", ignoreCase = true) ||
            name.endsWith(".tar", ignoreCase = true) ||
            name.endsWith(".gz", ignoreCase = true) -> Icons.Filled.Archive to archiveColor
        name.endsWith(".txt", ignoreCase = true) ||
            name.endsWith(".md", ignoreCase = true) ||
            name.endsWith(".pdf", ignoreCase = true) ||
            name.endsWith(".doc", ignoreCase = true) ||
            name.endsWith(".docx", ignoreCase = true) -> Icons.Filled.Description to textColor
        name.endsWith(".kt", ignoreCase = true) ||
            name.endsWith(".java", ignoreCase = true) ||
            name.endsWith(".py", ignoreCase = true) ||
            name.endsWith(".js", ignoreCase = true) ||
            name.endsWith(".c", ignoreCase = true) ||
            name.endsWith(".cpp", ignoreCase = true) ||
            name.endsWith(".xml", ignoreCase = true) ||
            name.endsWith(".json", ignoreCase = true) -> Icons.Filled.Code to codeColor
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to default
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: Map<String, String>,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSelect: () -> Unit = {},
    onDelete: () -> Unit,
    onPull: () -> Unit,
    onRename: () -> Unit = {},
    onMove: () -> Unit = {},
    onCopy: () -> Unit = {},
    onPreview: () -> Unit = {}
) {
    val isDir = file["isDirectory"] == "true"
    val name = file["name"] ?: ""
    val size = file["size"] ?: ""
    val date = file["date"] ?: ""
    val permissions = file["permissions"] ?: ""

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            val icon = getFileIcon(name, isDir)
            Icon(
                imageVector = icon.first,
                contentDescription = null,
                tint = icon.second,
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
                        DropdownMenuItem(
                            text = { Text(LocalStrings.current.preview) },
                            onClick = {
                                showMenu = false
                                onPreview()
                            },
                            leadingIcon = { Icon(Icons.Filled.Visibility, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.rename) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.move) },
                        onClick = {
                            showMenu = false
                            onMove()
                        },
                        leadingIcon = { @Suppress("DEPRECATION") Icon(Icons.Filled.DriveFileMove, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.copy) },
                        onClick = {
                            showMenu = false
                            onCopy()
                        },
                        leadingIcon = { Icon(Icons.Filled.FileCopy, null) }
                    )
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

@Composable
fun PreviewFileDialog(
    preview: com.adbkit.app.ui.viewmodel.FilePreview,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 400.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (preview.type) {
                        com.adbkit.app.ui.viewmodel.FilePreviewType.TEXT -> {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = preview.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        com.adbkit.app.ui.viewmodel.FilePreviewType.IMAGE -> {
                            if (preview.bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = preview.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = preview.error.ifEmpty { strings.previewImageError },
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        com.adbkit.app.ui.viewmodel.FilePreviewType.UNSUPPORTED -> {
                            Text(
                                text = preview.error.ifEmpty { strings.unsupportedPreview },
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}
