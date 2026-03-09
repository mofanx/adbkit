package com.adbkit.app.data.model

data class DeviceConnection(
    val id: String = "",
    val name: String = "",
    val ip: String = "",
    val port: Int = 5555,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val isWireless: Boolean = true
) {
    val address: String get() = "$ip:$port"
}

enum class ConnectionStatus {
    CONNECTED, DISCONNECTED, CONNECTING, UNAUTHORIZED, OFFLINE
}

data class DeviceInfo(
    val model: String = "",
    val brand: String = "",
    val device: String = "",
    val androidVersion: String = "",
    val sdkVersion: String = "",
    val buildId: String = "",
    val serialNumber: String = "",
    val hardware: String = "",
    val cpuAbi: String = "",
    val screenResolution: String = "",
    val screenDensity: String = "",
    val totalMemory: String = "",
    val availableMemory: String = "",
    val totalStorage: String = "",
    val availableStorage: String = "",
    val batteryLevel: String = "",
    val batteryStatus: String = "",
    val batteryTemperature: String = "",
    val wifiMac: String = "",
    val ipAddress: String = "",
    val bluetoothMac: String = "",
    val imei: String = "",
    val uptime: String = "",
    val kernelVersion: String = "",
    val baseband: String = "",
    val javaHeap: String = "",
    val openGlVersion: String = "",
    val displayRefreshRate: String = ""
)

data class AppInfo(
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val installedDate: String = "",
    val updatedDate: String = "",
    val apkSize: String = "",
    val dataSize: String = "",
    val apkPath: String = ""
)

data class ProcessInfo(
    val pid: String = "",
    val user: String = "",
    val name: String = "",
    val cpuUsage: String = "",
    val memoryUsage: String = "",
    val status: String = ""
)

data class FileItem(
    val name: String = "",
    val path: String = "",
    val isDirectory: Boolean = false,
    val size: Long = 0,
    val permissions: String = "",
    val modifiedDate: String = "",
    val owner: String = "",
    val group: String = ""
) {
    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> "${"%.1f".format(size.toDouble() / (1024 * 1024 * 1024))}GB"
        }
}

data class RemoteControlConfig(
    val resolution: String = "自动调整",
    val bitrate: String = "8Mbps",
    val aspectRatio: String = "保持原始比例",
    val navBarPosition: String = "悬浮",
    val fullscreen: Boolean = false,
    val screenOff: Boolean = false,
    val compatMode: Boolean = false
)
