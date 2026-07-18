package com.adbkit.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.ui.components.ConfirmDialog
import com.adbkit.app.ui.components.EmptyDevicePlaceholder
import com.adbkit.app.ui.components.EmptyState
import com.adbkit.app.ui.components.LoadingState
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.AppManagerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onMenuClick: () -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(AdbKitApplication.instance) }
    val confirmDangerous by settingsRepo.confirmDangerous.collectAsState(initial = true)

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingMessage by remember { mutableStateOf("") }

    fun runDangerous(title: String, message: String, action: () -> Unit) {
        if (confirmDangerous) {
            pendingTitle = title
            pendingMessage = message
            pendingAction = action
        } else {
            action()
        }
    }

    // APK file picker
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: "install_${System.currentTimeMillis()}.apk"

            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(it)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.installApk(cacheFile.absolutePath)
        }
    }

    LaunchedEffect(uiState.requestApkPick) {
        if (uiState.requestApkPick) {
            viewModel.onApkPickHandled()
            apkPickerLauncher.launch("application/vnd.android.package-archive")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenAppManager) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.batchBackupFiltered() }) {
                        Icon(Icons.Filled.Save, contentDescription = strings.appBackupApk)
                    }
                    IconButton(onClick = { viewModel.exportAppList(context) }) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.List, contentDescription = strings.exportAppList)
                    }
                    IconButton(onClick = { viewModel.requestInstallApk() }) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = strings.installApk)
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                    }
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = strings.search)
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
            // Tab row: User apps vs System apps
            TabRow(selectedTabIndex = if (uiState.showSystemApps) 1 else 0) {
                Tab(
                    selected = !uiState.showSystemApps,
                    onClick = { viewModel.setShowSystemApps(false) },
                    text = { Text(strings.userApps(uiState.userAppCount)) }
                )
                Tab(
                    selected = uiState.showSystemApps,
                    onClick = { viewModel.setShowSystemApps(true) },
                    text = { Text(strings.systemApps(uiState.systemAppCount)) }
                )
            }

            // Search bar
            if (uiState.showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(strings.searchPackage) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                Icon(Icons.Filled.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
            }

            if (uiState.isLoading) {
                LoadingState(message = strings.loadingAppList)
            } else if (uiState.error.isNotEmpty()) {
                EmptyDevicePlaceholder(
                    onRetry = { viewModel.refresh() },
                    message = uiState.error
                )
            } else if (uiState.filteredPackages.isEmpty()) {
                EmptyState(
                    title = strings.noData,
                    actionLabel = strings.refresh,
                    onAction = { viewModel.refresh() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(uiState.filteredPackages) { pkg ->
                        AppItemRow(
                            packageName = pkg,
                            onForceStop = { runDangerous(strings.appForceStop, "Force stop $pkg?", { viewModel.forceStop(pkg) }) },
                            onUninstall = { runDangerous(strings.appUninstall, "Uninstall $pkg? This cannot be undone.", { viewModel.uninstall(pkg) }) },
                            onClearData = { runDangerous(strings.appClearData, "Clear all data of $pkg?", { viewModel.clearData(pkg) }) },
                            onDisable = { runDangerous(strings.appFreeze, "Disable $pkg? System apps may become unstable.", { viewModel.disable(pkg) }) },
                            onEnable = { viewModel.enable(pkg) },
                            onBackup = { viewModel.backup(pkg) },
                            onLaunch = { viewModel.launch(pkg) },
                            onShowDetail = { viewModel.showDetail(pkg) }
                        )
                    }
                }
            }
        }

        // App detail dialog
        if (uiState.showDetailDialog && uiState.selectedPackage.isNotEmpty()) {
            AppDetailDialog(
                packageName = uiState.selectedPackage,
                details = uiState.appDetails,
                permissions = uiState.appPermissions,
                icon = uiState.appIcon,
                components = uiState.appComponents,
                onDismiss = { viewModel.hideDetail() }
            )
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

        // Status snackbar
        if (uiState.statusMessage.isNotEmpty()) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearStatus() }) { Text(strings.ok) }
                }
            ) {
                Text(uiState.statusMessage)
            }
        }
    }
}

@Composable
fun AppItemRow(
    packageName: String,
    onForceStop: () -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onBackup: () -> Unit,
    onLaunch: () -> Unit,
    onShowDetail: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onShowDetail,
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
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = LocalStrings.current.action)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appLaunch) },
                        onClick = { showMenu = false; onLaunch() },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Launch, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appForceStop) },
                        onClick = { showMenu = false; onForceStop() },
                        leadingIcon = { Icon(Icons.Filled.Stop, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appClearData) },
                        onClick = { showMenu = false; onClearData() },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appUninstall) },
                        onClick = { showMenu = false; onUninstall() },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appFreeze) },
                        onClick = { showMenu = false; onDisable() },
                        leadingIcon = { Icon(Icons.Filled.Block, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appEnable) },
                        onClick = { showMenu = false; onEnable() },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appBackupApk) },
                        onClick = { showMenu = false; onBackup() },
                        leadingIcon = { Icon(Icons.Filled.Save, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(LocalStrings.current.appDetails) },
                        onClick = { showMenu = false; onShowDetail() },
                        leadingIcon = { Icon(Icons.Filled.Info, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppDetailDialog(
    packageName: String,
    details: Map<String, String>,
    permissions: List<String> = emptyList(),
    icon: Bitmap? = null,
    components: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val keyLabels = mapOf(
        "version_name" to strings.adVersionName,
        "version_code" to strings.adVersionCode,
        "install_time" to strings.adInstallTime,
        "update_time" to strings.adUpdateTime,
        "apk_path" to strings.adApkPath,
        "data_dir" to strings.adDataDir,
        "target_sdk" to strings.adTargetSdk,
        "min_sdk" to strings.adMinSdk
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packageName, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyColumn {
                item {
                    if (icon != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    if (components.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ComponentChip(label = strings.activities, count = components["activities"] ?: "0")
                            ComponentChip(label = strings.services, count = components["services"] ?: "0")
                            ComponentChip(label = strings.receivers, count = components["receivers"] ?: "0")
                            ComponentChip(label = strings.providers, count = components["providers"] ?: "0")
                        }
                    }
                }
                items(details.toList()) { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = keyLabels[key] ?: key,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(80.dp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (permissions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Permissions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(permissions) { permission ->
                        val cleaned = permission
                            .substringAfterLast(".")
                            .replace("_", " ")
                            .replace("permission", "", ignoreCase = true)
                            .trim()
                        Text(
                            text = "• ${if (cleaned.isNotBlank()) cleaned else permission}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.close) }
        }
    )
}

@Composable
fun ComponentChip(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
