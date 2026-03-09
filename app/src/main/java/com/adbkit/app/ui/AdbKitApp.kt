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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adbkit.app.ui.navigation.Screen
import com.adbkit.app.ui.screens.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbKitApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                            text = "ADB Kit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "专业ADB助手",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Navigation items
                Screen.drawerScreens.forEach { screen ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == screen.route) screen.selectedIcon else screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
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

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()

                // Settings & Exit
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.DeviceInfo.route) {
                DeviceInfoScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.Tools.route) {
                ToolsScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.RemoteControl.route) {
                RemoteControlScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.FileManager.route) {
                FileManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.AppManager.route) {
                AppManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.ProcessManager.route) {
                ProcessManagerScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.Terminal.route) {
                TerminalScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.Fastboot.route) {
                FastbootScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
        }
    }
}
