package com.adbkit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adbkit.app.ui.navigation.Routes
import com.adbkit.app.ui.navigation.Screen
import com.adbkit.app.ui.strings.LocalStrings

data class QuickNavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
)

@Composable
fun QuickNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val items = listOf(
        QuickNavItem(Routes.DEVICE_INFO, Screen.DeviceInfo.icon, Screen.DeviceInfo.selectedIcon, strings.screenDeviceInfo),
        QuickNavItem(Routes.REMOTE_CONTROL, Screen.RemoteControl.icon, Screen.RemoteControl.selectedIcon, strings.screenRemoteControl),
        QuickNavItem(Routes.FILE_MANAGER, Screen.FileManager.icon, Screen.FileManager.selectedIcon, strings.screenFileManager),
        QuickNavItem(Routes.APP_MANAGER, Screen.AppManager.icon, Screen.AppManager.selectedIcon, strings.screenAppManager),
        QuickNavItem(Routes.PROCESS_MANAGER, Screen.ProcessManager.icon, Screen.ProcessManager.selectedIcon, strings.screenProcessManager),
        QuickNavItem(Routes.TERMINAL, Screen.Terminal.icon, Screen.Terminal.selectedIcon, strings.screenTerminal),
        QuickNavItem(Routes.TOOLS, Screen.Tools.icon, Screen.Tools.selectedIcon, strings.screenTools),
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                IconButton(
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
