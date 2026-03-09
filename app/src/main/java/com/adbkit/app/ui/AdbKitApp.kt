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
import com.adbkit.app.ui.screens.*
import com.adbkit.app.ui.strings.*
import com.adbkit.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKitApp(settingsViewModel: SettingsViewModel = viewModel()) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val strings: AppStrings = if (settingsState.language == "en") EnStrings else ZhStrings

    CompositionLocalProvider(LocalStrings provides strings) {
        AdbKitContent()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.appSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateToFastboot = {
                        navController.navigate(Routes.FASTBOOT) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
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
