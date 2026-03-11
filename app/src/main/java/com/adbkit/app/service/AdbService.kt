package com.adbkit.app.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.adbkit.app.AdbKitApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object AdbService {

    private val _currentDevice = MutableStateFlow<String?>(null)
    val currentDevice: StateFlow<String?> = _currentDevice.asStateFlow()
    
    private var adbPath: String = "adb"
    private var fastbootPath: String = "fastboot"

    fun setAdbPath(path: String) {
        adbPath = path.ifBlank { "adb" }
    }

    fun getAdbPath(): String = adbPath

    fun setFastbootPath(path: String) {
        fastbootPath = path.ifBlank { "fastboot" }
    }

    fun getFastbootPath(): String = fastbootPath

    fun setCurrentDevice(address: String?) {
        _currentDevice.value = address
    }

    fun getCurrentDevice(): String? = _currentDevice.value

    /**
     * Get HOME directory for ADB server.
     * ADB needs $HOME/.android for auth keys. The default /data is not writable.
     */
    private fun getAdbHome(): String {
        return AdbKitApplication.instance.filesDir.absolutePath
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder("sh", "-c", command)
            pb.environment()["HOME"] = getAdbHome()
            pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
            val process = pb.start()
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()
            CommandResult(
                success = exitCode == 0,
                output = stdout.trim(),
                error = stderr.trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }

    suspend fun adb(vararg args: String): CommandResult {
        val device = _currentDevice.value
        val cmd = buildString {
            append(adbPath)
            if (device != null) {
                append(" -s $device")
            }
            args.forEach { append(" $it") }
        }
        return executeCommand(cmd)
    }

    suspend fun shell(command: String): CommandResult {
        return adb("shell", command)
    }

    suspend fun connect(address: String): CommandResult {
        return executeCommand("$adbPath connect $address")
    }

    suspend fun disconnect(address: String): CommandResult {
        return executeCommand("$adbPath disconnect $address")
    }

    suspend fun disconnectAll(): CommandResult {
        return executeCommand("$adbPath disconnect")
    }

    suspend fun killServer(): CommandResult {
        return executeCommand("$adbPath kill-server")
    }

    suspend fun startServer(): CommandResult {
        return executeCommand("$adbPath start-server")
    }

    suspend fun restartServer(): CommandResult {
        killServer()
        return startServer()
    }

    suspend fun getConnectedDevices(): List<String> {
        val result = executeCommand("$adbPath devices -l")
        if (!result.success) return emptyList()
        return result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2 && parts[1] != "offline") parts[0] else null
            }
    }

    suspend fun getDeviceProp(prop: String): String {
        val result = shell("getprop $prop")
        return if (result.success) result.output.trim() else ""
    }

    suspend fun getDeviceInfo(): Map<String, String> {
        val props = mapOf(
            "model" to "ro.product.model",
            "brand" to "ro.product.brand",
            "device_name" to "ro.product.device",
            "android_version" to "ro.build.version.release",
            "sdk_version" to "ro.build.version.sdk",
            "build_id" to "ro.build.display.id",
            "serial" to "ro.serialno",
            "hardware" to "ro.hardware",
            "cpu_arch" to "ro.product.cpu.abi",
            "security_patch" to "ro.build.version.security_patch",
            "baseband" to "gsm.version.baseband",
            "kernel" to "os.version"
        )
        val result = mutableMapOf<String, String>()
        for ((label, prop) in props) {
            result[label] = getDeviceProp(prop)
        }
        // Extra info via shell commands
        val screenSize = shell("wm size")
        if (screenSize.success) result["screen_resolution"] = screenSize.output.replace("Physical size: ", "")

        val density = shell("wm density")
        if (density.success) result["screen_density"] = density.output.replace("Physical density: ", "")

        val battery = shell("dumpsys battery")
        if (battery.success) {
            battery.output.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("level:") -> result["battery_level"] = "${trimmed.substringAfter(":").trim()}%"
                    trimmed.startsWith("temperature:") -> {
                        val temp = trimmed.substringAfter(":").trim().toIntOrNull()
                        if (temp != null) result["battery_temp"] = "${temp / 10.0}°C"
                    }
                    trimmed.startsWith("status:") -> {
                        val status = trimmed.substringAfter(":").trim()
                        result["battery_status"] = status
                    }
                }
            }
        }

        val memInfo = shell("cat /proc/meminfo")
        if (memInfo.success) {
            val lines = memInfo.output.lines()
            val totalLine = lines.find { it.startsWith("MemTotal:") }
            val availLine = lines.find { it.startsWith("MemAvailable:") }
            totalLine?.let {
                val kb = it.replace("MemTotal:", "").replace("kB", "").trim().toLongOrNull()
                if (kb != null) result["total_memory"] = "${kb / 1024}MB"
            }
            availLine?.let {
                val kb = it.replace("MemAvailable:", "").replace("kB", "").trim().toLongOrNull()
                if (kb != null) result["available_memory"] = "${kb / 1024}MB"
            }
        }

        val dfResult = shell("df /data")
        if (dfResult.success) {
            val dataLine = dfResult.output.lines().lastOrNull { it.contains("/data") }
            dataLine?.let {
                val parts = it.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    result["total_storage"] = parts[1]
                    result["available_storage"] = parts[3]
                }
            }
        }

        val uptimeResult = shell("uptime")
        if (uptimeResult.success) result["uptime"] = uptimeResult.output.trim()

        val ipResult = shell("ip route | grep 'src'")
        if (ipResult.success) {
            val ip = ipResult.output.lines().firstOrNull()?.let { line ->
                "src\\s+(\\S+)".toRegex().find(line)?.groupValues?.getOrNull(1)
            }
            if (ip != null) result["ip_address"] = ip
        }

        val wifiMac = shell("cat /sys/class/net/wlan0/address")
        if (wifiMac.success) result["wifi_mac"] = wifiMac.output.trim()

        return result
    }

    suspend fun listFiles(path: String): List<Map<String, String>> {
        val result = shell("ls -la '$path'")
        if (!result.success) return emptyList()
        return result.output.lines()
            .filter { it.isNotBlank() && !it.startsWith("total") }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex(), limit = 9)
                if (parts.size >= 8) {
                    val name = if (parts.size >= 9) parts[8] else parts[7]
                    if (name == "." || name == "..") return@mapNotNull null
                    mapOf(
                        "permissions" to parts[0],
                        "owner" to parts[2],
                        "group" to parts[3],
                        "size" to (parts.getOrNull(4) ?: "0"),
                        "date" to "${parts.getOrNull(5) ?: ""} ${parts.getOrNull(6) ?: ""}",
                        "name" to name,
                        "isDirectory" to (parts[0].startsWith("d")).toString(),
                        "path" to "$path/$name"
                    )
                } else null
            }
    }

    suspend fun pullFile(remotePath: String, localPath: String): CommandResult {
        return adb("pull", "'$remotePath'", "'$localPath'")
    }

    suspend fun pushFile(localPath: String, remotePath: String): CommandResult {
        return adb("push", "'$localPath'", "'$remotePath'")
    }

    suspend fun deleteFile(path: String): CommandResult {
        return shell("rm -rf '$path'")
    }

    suspend fun createDirectory(path: String): CommandResult {
        return shell("mkdir -p '$path'")
    }

    suspend fun getInstalledPackages(systemApps: Boolean = false): List<String> {
        val flag = if (systemApps) "-s" else "-3"
        val result = shell("pm list packages $flag")
        if (!result.success) return emptyList()
        return result.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .sorted()
    }

    suspend fun getAppDetail(packageName: String): Map<String, String> {
        val info = mutableMapOf<String, String>()
        val dump = shell("dumpsys package $packageName")
        if (dump.success) {
            dump.output.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("versionName=") -> info["version_name"] = trimmed.substringAfter("=")
                    trimmed.startsWith("versionCode=") -> info["version_code"] = trimmed.substringAfter("=").split(" ")[0]
                    trimmed.startsWith("firstInstallTime=") -> info["install_time"] = trimmed.substringAfter("=")
                    trimmed.startsWith("lastUpdateTime=") -> info["update_time"] = trimmed.substringAfter("=")
                    trimmed.startsWith("codePath=") -> info["apk_path"] = trimmed.substringAfter("=")
                    trimmed.startsWith("dataDir=") -> info["data_dir"] = trimmed.substringAfter("=")
                    trimmed.startsWith("targetSdk=") -> info["target_sdk"] = trimmed.substringAfter("=")
                    trimmed.startsWith("minSdk=") -> info["min_sdk"] = trimmed.substringAfter("=")
                }
            }
        }
        return info
    }

    suspend fun installApp(apkPath: String): CommandResult {
        return adb("install", "-r", "'$apkPath'")
    }

    suspend fun uninstallApp(packageName: String, keepData: Boolean = false): CommandResult {
        return if (keepData) {
            adb("uninstall", "-k", packageName)
        } else {
            adb("uninstall", packageName)
        }
    }

    suspend fun forceStopApp(packageName: String): CommandResult {
        return shell("am force-stop $packageName")
    }

    suspend fun clearAppData(packageName: String): CommandResult {
        return shell("pm clear $packageName")
    }

    suspend fun disableApp(packageName: String): CommandResult {
        return shell("pm disable-user --user 0 $packageName")
    }

    suspend fun enableApp(packageName: String): CommandResult {
        return shell("pm enable $packageName")
    }

    suspend fun getProcessList(): List<Map<String, String>> {
        val result = shell("ps -A -o PID,USER,RSS,%CPU,NAME")
        if (!result.success) return emptyList()
        return result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex(), limit = 5)
                if (parts.size >= 5) {
                    mapOf(
                        "pid" to parts[0],
                        "user" to parts[1],
                        "memory" to parts[2],
                        "cpu" to parts[3],
                        "name" to parts[4]
                    )
                } else null
            }
    }

    suspend fun killProcess(pid: String): CommandResult {
        return shell("kill -9 $pid")
    }

    suspend fun getRunningApps(): List<Map<String, String>> {
        // Get running app processes with memory usage via dumpsys meminfo
        val result = shell("dumpsys meminfo --local -s")
        if (!result.success) return emptyList()
        val apps = mutableListOf<Map<String, String>>()
        // Parse "Total PSS by process:" section
        var inSection = false
        result.output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Total PSS by process:") || trimmed.startsWith("Total RAM by process:")) {
                inSection = true
                return@forEach
            }
            if (inSection && trimmed.isBlank()) {
                inSection = false
                return@forEach
            }
            if (inSection) {
                // Format: "123,456K: com.example.app (pid 1234 / activities)"
                // or "123,456K: com.example.app (pid 1234)"
                val match = "^([\\d,]+)K:\\s+(\\S+)\\s+\\(pid\\s+(\\d+)".toRegex().find(trimmed)
                if (match != null) {
                    val memKbStr = match.groupValues[1].replace(",", "")
                    val name = match.groupValues[2]
                    val pid = match.groupValues[3]
                    apps.add(mapOf(
                        "name" to name,
                        "pid" to pid,
                        "memory" to memKbStr
                    ))
                }
            }
        }
        return apps
    }

    suspend fun forceStopAndRefresh(packageName: String): CommandResult {
        val result = forceStopApp(packageName)
        return result
    }

    suspend fun takeScreenshot(savePath: String): CommandResult {
        val remotePath = "/sdcard/screenshot_temp.png"
        val cap = shell("screencap -p $remotePath")
        if (!cap.success) return cap
        val pull = adb("pull", remotePath, "'$savePath'")
        shell("rm $remotePath")
        return pull
    }

    suspend fun startScreenRecord(savePath: String, timeLimit: Int = 180): CommandResult {
        val remotePath = "/sdcard/screenrecord_temp.mp4"
        return shell("screenrecord --time-limit $timeLimit $remotePath")
    }

    suspend fun reboot(mode: String = ""): CommandResult {
        return if (mode.isEmpty()) {
            adb("reboot")
        } else {
            adb("reboot", mode)
        }
    }

    suspend fun fastbootCommand(vararg args: String): CommandResult {
        val cmd = "$fastbootPath ${args.joinToString(" ")}"
        return executeCommand(cmd)
    }

    suspend fun fastbootFlash(partition: String, imagePath: String): CommandResult {
        return fastbootCommand("flash", partition, imagePath)
    }

    suspend fun fastbootDevices(): CommandResult {
        return executeCommand("$fastbootPath devices")
    }

    suspend fun inputText(text: String): CommandResult {
        return shell("input text '${text.replace("'", "\\'")}'")
    }

    suspend fun inputKeyEvent(keyCode: Int): CommandResult {
        return shell("input keyevent $keyCode")
    }

    suspend fun inputTap(x: Int, y: Int): CommandResult {
        return shell("input tap $x $y")
    }

    suspend fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300): CommandResult {
        return shell("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    suspend fun setScreenBrightness(value: Int): CommandResult {
        return shell("settings put system screen_brightness $value")
    }

    suspend fun setScreenTimeout(ms: Int): CommandResult {
        return shell("settings put system screen_off_timeout $ms")
    }

    suspend fun getWifiStatus(): CommandResult {
        return shell("dumpsys wifi | grep 'Wi-Fi is'")
    }

    suspend fun toggleWifi(enable: Boolean): CommandResult {
        return shell("svc wifi ${if (enable) "enable" else "disable"}")
    }

    suspend fun toggleBluetooth(enable: Boolean): CommandResult {
        return shell("svc bluetooth ${if (enable) "enable" else "disable"}")
    }

    suspend fun toggleAirplaneMode(enable: Boolean): CommandResult {
        val value = if (enable) "1" else "0"
        shell("settings put global airplane_mode_on $value")
        return shell("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enable")
    }

    suspend fun openUrl(url: String): CommandResult {
        return shell("am start -a android.intent.action.VIEW -d '$url'")
    }

    suspend fun launchApp(packageName: String): CommandResult {
        return shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    suspend fun getLogcat(lines: Int = 100, filter: String = ""): CommandResult {
        val cmd = if (filter.isNotEmpty()) {
            "logcat -d -t $lines | grep -i '$filter'"
        } else {
            "logcat -d -t $lines"
        }
        return shell(cmd)
    }

    suspend fun clearLogcat(): CommandResult {
        return shell("logcat -c")
    }

    suspend fun backupApp(packageName: String, savePath: String): CommandResult {
        val apkPath = shell("pm path $packageName")
        if (!apkPath.success) return apkPath
        val path = apkPath.output.replace("package:", "").trim()
        return adb("pull", path, "'$savePath'")
    }

    suspend fun grantPermission(packageName: String, permission: String): CommandResult {
        return shell("pm grant $packageName $permission")
    }

    suspend fun revokePermission(packageName: String, permission: String): CommandResult {
        return shell("pm revoke $packageName $permission")
    }

    suspend fun getAppPermissions(packageName: String): List<String> {
        val result = shell("dumpsys package $packageName | grep permission")
        if (!result.success) return emptyList()
        return result.output.lines()
            .filter { it.contains("android.permission.") }
            .map { it.trim() }
    }

    suspend fun setSystemProp(prop: String, value: String): CommandResult {
        return shell("setprop $prop $value")
    }

    suspend fun dumpActivity(): CommandResult {
        return shell("dumpsys activity top | grep ACTIVITY")
    }

    suspend fun captureScreenBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val device = _currentDevice.value
            val cmd = buildString {
                append(adbPath)
                if (device != null) append(" -s $device")
                append(" exec-out screencap -p")
            }
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.environment()["HOME"] = getAdbHome()
            pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
            val process = pb.start()
            val bytes = process.inputStream.readBytes()
            process.waitFor()
            if (bytes.size > 100) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
