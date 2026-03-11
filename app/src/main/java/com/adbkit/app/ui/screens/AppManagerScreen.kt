package com.adbkit.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(strings.loadingAppList)
                    }
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) { Text(strings.retry) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(uiState.filteredPackages) { pkg ->
                        AppItemRow(
                            packageName = pkg,
                            onForceStop = { viewModel.forceStop(pkg) },
                            onUninstall = { viewModel.uninstall(pkg) },
                            onClearData = { viewModel.clearData(pkg) },
                            onDisable = { viewModel.disable(pkg) },
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
                onDismiss = { viewModel.hideDetail() }
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.close) }
        }
    )
}
