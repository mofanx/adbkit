package com.adbkit.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    data object Home : Screen("home", "连接", Icons.Outlined.Link, Icons.Filled.Link)
    data object DeviceInfo : Screen("device_info", "设备信息", Icons.Outlined.Info, Icons.Filled.Info)
    data object Tools : Screen("tools", "实用工具", Icons.Outlined.Build, Icons.Filled.Build)
    data object RemoteControl : Screen("remote_control", "远程控制", Icons.Outlined.Mouse, Icons.Filled.Mouse)
    data object FileManager : Screen("file_manager", "文件管理", Icons.Outlined.Folder, Icons.Filled.Folder)
    data object AppManager : Screen("app_manager", "应用管理", Icons.Outlined.Apps, Icons.Filled.Apps)
    data object ProcessManager : Screen("process_manager", "进程管理", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle)
    data object Terminal : Screen("terminal", "运行命令", Icons.Outlined.Terminal, Icons.Filled.Terminal)
    data object Fastboot : Screen("fastboot", "Fastboot", Icons.Outlined.FlashOn, Icons.Filled.FlashOn)
    data object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)

    companion object {
        val drawerScreens = listOf(
            Home, DeviceInfo, Tools, RemoteControl,
            FileManager, AppManager, ProcessManager, Terminal, Fastboot
        )
    }
}
