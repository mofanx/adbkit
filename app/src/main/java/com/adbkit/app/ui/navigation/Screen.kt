package com.adbkit.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val DEVICE_INFO = "device_info"
    const val TOOLS = "tools"
    const val REMOTE_CONTROL = "remote_control"
    const val FILE_MANAGER = "file_manager"
    const val APP_MANAGER = "app_manager"
    const val PROCESS_MANAGER = "process_manager"
    const val TERMINAL = "terminal"
    const val FASTBOOT = "fastboot"
    const val SETTINGS = "settings"
}

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    object Home : Screen(Routes.HOME, "连接", Icons.Outlined.Link, Icons.Filled.Link)
    object DeviceInfo : Screen(Routes.DEVICE_INFO, "设备信息", Icons.Outlined.Info, Icons.Filled.Info)
    object Tools : Screen(Routes.TOOLS, "实用工具", Icons.Outlined.Build, Icons.Filled.Build)
    object RemoteControl : Screen(Routes.REMOTE_CONTROL, "远程控制", Icons.Outlined.Phonelink, Icons.Filled.Phonelink)
    object FileManager : Screen(Routes.FILE_MANAGER, "文件管理", Icons.Outlined.Folder, Icons.Filled.Folder)
    object AppManager : Screen(Routes.APP_MANAGER, "应用管理", Icons.Outlined.Apps, Icons.Filled.Apps)
    object ProcessManager : Screen(Routes.PROCESS_MANAGER, "进程管理", Icons.Outlined.Memory, Icons.Filled.Memory)
    object Terminal : Screen(Routes.TERMINAL, "运行命令", Icons.Outlined.Code, Icons.Filled.Code)
    object Fastboot : Screen(Routes.FASTBOOT, "Fastboot", Icons.Outlined.FlashOn, Icons.Filled.FlashOn)
    object Settings : Screen(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings)

    companion object {
        val drawerScreens: List<Screen>
            get() = listOf(
                Home, DeviceInfo, Tools, RemoteControl,
                FileManager, AppManager, ProcessManager, Terminal
            )
    }
}
