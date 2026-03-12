package com.adbkit.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

// Pages available when connected, shown in HorizontalPager
private val pagerScreens = listOf(
    Screen.DeviceInfo,
    Screen.RemoteControl,
    Screen.FileManager,
    Screen.AppManager,
    Screen.ProcessManager,
    Screen.Terminal,
    Screen.Tools
)

/**
 * App content with simple state-based navigation:
 * - "home": Home/connection page
 * - "connected": HorizontalPager with all functional pages
 * - "settings": Settings page
 * - "fastboot": Fastboot page
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdbKitContent() {
    val strings = LocalStrings.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val currentDevice by com.adbkit.app.service.AdbService.currentDevice.collectAsState()

    // Simple navigation state
    var currentView by remember { mutableStateOf("home") }
    // Track the device that was used when entering connected view, to detect switch
    var connectedDeviceKey by remember { mutableStateOf<String?>(null) }

    // Pager state for connected pages
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pagerScreens.size }
    )

    // Auto-navigate to home when device disconnects
    LaunchedEffect(currentDevice) {
        if (currentDevice == null && currentView == "connected") {
            currentView = "home"
        }
    }

    fun performDisconnect() {
        scope.launch {
            drawerState.close()
            currentDevice?.let { com.adbkit.app.service.AdbService.disconnect(it) }
            com.adbkit.app.service.AdbService.setCurrentDevice(null)
            pagerState.scrollToPage(0)
            currentView = "home"
        }
    }

    // Double-press back to disconnect (with toast on first press)
    var lastBackPressTime by remember { mutableStateOf(0L) }
    // Track fullscreen state for remote control page
    var isRemoteFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = currentView != "home") {
        if (currentView == "connected" && currentDevice != null) {
            // If in fullscreen remote control, exit fullscreen first
            if (isRemoteFullscreen) {
                isRemoteFullscreen = false
                return@BackHandler
            }
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                performDisconnect()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, strings.pressBackAgainToExit, Toast.LENGTH_SHORT).show()
            }
        } else {
            currentView = "home"
        }
    }

    // Only show drawer when connected
    val isConnectedView = currentView == "connected" && currentDevice != null

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Always allow gestures so scrim click can close drawer
        gesturesEnabled = drawerState.isOpen || currentView == "home",
        drawerContent = {
            if (isConnectedView) {
                DrawerContent(
                    strings = strings,
                    currentDevice = currentDevice,
                    pagerState = pagerState,
                    pagerScreens = pagerScreens,
                    drawerState = drawerState,
                    onDisconnect = { performDisconnect() }
                )
            } else {
                ModalDrawerSheet(modifier = Modifier.width(240.dp)) {}
            }
        }
    ) {
        when (currentView) {
            "home" -> {
                HomeScreen(
                    onMenuClick = {},
                    onNavigateToFastboot = { currentView = "fastboot" },
                    onNavigateToSettings = { currentView = "settings" },
                    onDeviceClick = { device ->
                        scope.launch {
                            // Reset pager when switching to a different device
                            if (connectedDeviceKey != null && connectedDeviceKey != device) {
                                pagerState.scrollToPage(0)
                            }
                            connectedDeviceKey = device

                            val result = com.adbkit.app.service.AdbService.shell("echo ok")
                            if (result.success) {
                                currentView = "connected"
                            } else {
                                val connectResult = com.adbkit.app.service.AdbService.connect(device)
                                if (connectResult.success && connectResult.output.contains("connected")) {
                                    currentView = "connected"
                                } else {
                                    com.adbkit.app.service.AdbService.setCurrentDevice(null)
                                }
                            }
                        }
                    }
                )
            }
            "connected" -> {
                ConnectedPagerHost(
                    pagerState = pagerState,
                    drawerState = drawerState,
                    deviceKey = connectedDeviceKey,
                    isFullscreen = isRemoteFullscreen,
                    onFullscreenChanged = { isRemoteFullscreen = it }
                )
            }
            "settings" -> {
                SettingsScreen(onMenuClick = {
                    currentView = if (currentDevice != null) "connected" else "home"
                })
            }
            "fastboot" -> {
                FastbootScreen(onMenuClick = { currentView = "home" })
            }
        }
    }
}

@Composable
private fun DrawerContent(
    strings: AppStrings,
    currentDevice: String?,
    pagerState: PagerState,
    pagerScreens: List<Screen>,
    drawerState: DrawerState,
    onDisconnect: () -> Unit
) {
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(modifier = Modifier.width(240.dp)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.appSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                text = currentDevice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDisconnect,
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
                }
            }
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Drawer titles map
        val drawerTitles = mapOf(
            Screen.Home.route to strings.screenHome,
            Screen.DeviceInfo.route to strings.screenDeviceInfo,
            Screen.Tools.route to strings.screenTools,
            Screen.RemoteControl.route to strings.screenRemoteControl,
            Screen.FileManager.route to strings.screenFileManager,
            Screen.AppManager.route to strings.screenAppManager,
            Screen.ProcessManager.route to strings.screenProcessManager,
            Screen.Terminal.route to strings.screenTerminal,
        )

        // Page navigation items
        pagerScreens.forEachIndexed { index, screen ->
            val localTitle = drawerTitles[screen.route] ?: screen.title
            val isSelected = pagerState.currentPage == index
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon else screen.icon,
                        contentDescription = localTitle
                    )
                },
                label = { Text(localTitle) },
                selected = isSelected,
                onClick = {
                    scope.launch {
                        drawerState.close()
                        pagerState.animateScrollToPage(index)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * HorizontalPager for connected device pages.
 * Swipe is disabled only when Remote Control is actively connected (streaming).
 * Uses key(deviceKey) to force recomposition when switching devices, clearing old data.
 */
@Composable
private fun ConnectedPagerHost(
    pagerState: PagerState,
    drawerState: DrawerState,
    deviceKey: String?,
    isFullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRemoteControlConnected by remember { mutableStateOf(false) }
    val isRemoteControlPage = pagerState.currentPage == 1

    // key(deviceKey) forces full recomposition of all child screens when switching devices
    key(deviceKey) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !(isRemoteControlPage && isRemoteControlConnected),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
            when (page) {
                0 -> DeviceInfoScreen(onMenuClick = openDrawer)
                1 -> RemoteControlScreen(
                    onMenuClick = openDrawer,
                    onRemoteConnectedChanged = { isRemoteControlConnected = it }
                )
                2 -> FileManagerScreen(onMenuClick = openDrawer)
                3 -> AppManagerScreen(onMenuClick = openDrawer)
                4 -> ProcessManagerScreen(onMenuClick = openDrawer)
                5 -> TerminalScreen(onMenuClick = openDrawer)
                6 -> ToolsScreen(onMenuClick = openDrawer)
            }
        }
    }
}
