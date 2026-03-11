package com.adbkit.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adbkit.app.ui.navigation.Routes
import com.adbkit.app.ui.navigation.Screen
import androidx.compose.foundation.isSystemInDarkTheme
import com.adbkit.app.ui.screens.*
import com.adbkit.app.ui.strings.*
import com.adbkit.app.ui.theme.AdbKitTheme
import com.adbkit.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKitApp(settingsViewModel: SettingsViewModel = viewModel()) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val strings: AppStrings = if (settingsState.language == "en") EnStrings else ZhStrings

    val darkTheme = when (settingsState.darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    AdbKitTheme(
        darkTheme = darkTheme,
        dynamicColor = settingsState.dynamicColor
    ) {
        CompositionLocalProvider(LocalStrings provides strings) {
            AdbKitContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbKitContent() {
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Track current device for sidebar display (reactive)
    val currentDevice by com.adbkit.app.service.AdbService.currentDevice.collectAsState()

    val drawerTitles = mapOf(
        Routes.HOME to strings.screenHome,
        Routes.DEVICE_INFO to strings.screenDeviceInfo,
        Routes.TOOLS to strings.screenTools,
        Routes.REMOTE_CONTROL to strings.screenRemoteControl,
        Routes.FILE_MANAGER to strings.screenFileManager,
        Routes.APP_MANAGER to strings.screenAppManager,
        Routes.PROCESS_MANAGER to strings.screenProcessManager,
        Routes.TERMINAL to strings.screenTerminal,
    )

    fun navigateTo(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // Header with device info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.appName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.appSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Current device info
                        if (currentDevice != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = currentDevice ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                currentDevice?.let { com.adbkit.app.service.AdbService.disconnect(it) }
                                                com.adbkit.app.service.AdbService.setCurrentDevice(null)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.LinkOff,
                                            contentDescription = strings.disconnect,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.noDeviceConnected,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Navigation items
                Screen.drawerScreens.forEach { screen ->
                    val localTitle = drawerTitles[screen.route] ?: screen.title
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == screen.route) screen.selectedIcon else screen.icon,
                                contentDescription = localTitle
                            )
                        },
                        label = { Text(localTitle) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navigateTo(screen.route)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick nav bar when device is connected and not on home page
            if (currentDevice != null && currentRoute != Routes.HOME && currentRoute != Routes.SETTINGS && currentRoute != Routes.FASTBOOT) {
                QuickNavBar(
                    currentRoute = currentRoute ?: "",
                    onNavigate = { navigateTo(it) }
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.weight(1f)
            ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateToFastboot = { navigateTo(Routes.FASTBOOT) },
                    onNavigateToSettings = { navigateTo(Routes.SETTINGS) },
                    onDeviceClick = { device ->
                        // currentDevice is now reactive via StateFlow, no need to set manually
                        navigateTo(Routes.DEVICE_INFO)
                    }
                )
            }
            composable(Routes.DEVICE_INFO) {
                DeviceInfoScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.TOOLS) {
                ToolsScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.REMOTE_CONTROL) {
                RemoteControlScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.FILE_MANAGER) {
                FileManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.APP_MANAGER) {
                AppManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.PROCESS_MANAGER) {
                ProcessManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.TERMINAL) {
                TerminalScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.FASTBOOT) {
                FastbootScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            }
        }
    }
}
