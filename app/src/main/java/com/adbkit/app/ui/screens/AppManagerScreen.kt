package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.adbkit.app.ui.viewmodel.AppManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onMenuClick: () -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用管理") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
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
                    text = { Text("用户应用 (${uiState.userAppCount})") }
                )
                Tab(
                    selected = uiState.showSystemApps,
                    onClick = { viewModel.setShowSystemApps(true) },
                    text = { Text("系统应用 (${uiState.systemAppCount})") }
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
                    placeholder = { Text("搜索包名...") },
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
                        Text("正在加载应用列表...")
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
                        Button(onClick = { viewModel.refresh() }) { Text("重试") }
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
                    TextButton(onClick = { viewModel.clearStatus() }) { Text("确定") }
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
                    Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("启动") },
                        onClick = { showMenu = false; onLaunch() },
                        leadingIcon = { Icon(Icons.Filled.Launch, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("强制停止") },
                        onClick = { showMenu = false; onForceStop() },
                        leadingIcon = { Icon(Icons.Filled.Stop, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("清除数据") },
                        onClick = { showMenu = false; onClearData() },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("卸载") },
                        onClick = { showMenu = false; onUninstall() },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                    DropdownMenuItem(
                        text = { Text("冻结/禁用") },
                        onClick = { showMenu = false; onDisable() },
                        leadingIcon = { Icon(Icons.Filled.Block, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("启用") },
                        onClick = { showMenu = false; onEnable() },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("备份APK") },
                        onClick = { showMenu = false; onBackup() },
                        leadingIcon = { Icon(Icons.Filled.Save, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("详情") },
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
                            text = key,
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
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
