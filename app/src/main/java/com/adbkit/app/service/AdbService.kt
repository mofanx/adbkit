package com.adbkit.app.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.adbkit.app.AdbKitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object AdbService {

    private val _currentDevice = MutableStateFlow<String?>(null)
    val currentDevice: StateFlow<String?> = _currentDevice.asStateFlow()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

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
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (address != null) {
            heartbeatJob = serviceScope.launch {
                while (true) {
                    delay(5000)
                    val result = shell("echo ok")
                    if (!result.success) {
                        _currentDevice.value = null
                        break
                    }
                }
            }
        }
    }

    fun getCurrentDevice(): String? = _currentDevice.value

    suspend fun hasRootAccess(): Boolean {
        val result = shell("su -c 'id -u' 2>/dev/null")
        return result.success && result.output.trim() == "0"
    }

    /**
     * Get HOME directory for ADB server.
     * ADB needs $HOME/.android for auth keys. The default /data is not writable.
     */
    private fun mapBatteryHealth(health: Int): String {
        return when (health) {
            1 -> "UNKNOWN"
            2 -> "GOOD"
            3 -> "OVERHEAT"
            4 -> "DEAD"
            5 -> "OVER_VOLTAGE"
            6 -> "UNSPECIFIED_FAILURE"
            7 -> "COLD"
            else -> "UNKNOWN"
        }
    }

    private fun getAdbHome(): String {
        return AdbKitApplication.instance.filesDir.absolutePath
    }

    /**
     * Escape an argument for the device shell to avoid injection or quoting errors.
     */
    private fun shellQuote(s: String): String {
        return "'${s.replace("'", "'\\''")}'"
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val pb = ProcessBuilder("sh", "-c", command)
            pb.environment()["HOME"] = getAdbHome()
            pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
            process = pb.start()
            coroutineContext[Job]?.invokeOnCompletion { process?.destroyForcibly() }
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
        } finally {
            process?.destroyForcibly()
        }
    }

    suspend fun executeCommand(parts: List<String>): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val pb = ProcessBuilder(parts)
            pb.environment()["HOME"] = getAdbHome()
            pb.environment()["TMPDIR"] = AdbKitApplication.instance.cacheDir.absolutePath
            process = pb.start()
            coroutineContext[Job]?.invokeOnCompletion { process?.destroyForcibly() }
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
        } finally {
            process?.destroyForcibly()
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

    fun classifyConnectionError(result: CommandResult): String {
        val output = (result.error + " " + result.output).lowercase()
        return when {
            result.success && result.output.contains("connected") -> "connected"
            "connection refused" in output || "refused" in output -> "refused"
            "no route to host" in output || "unreachable" in output || "timed out" in output || "timeout" in output -> "unreachable"
            "offline" in output || "device offline" in output -> "offline"
            "failed to authenticate" in output || "auth" in output || "unauthorized" in output -> "auth"
            "cannot resolve" in output || "unknown host" in output || "no address" in output -> "invalid"
            else -> "failed"
        }
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
        val result = shell("getprop ${shellQuote(prop)}")
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
                    trimmed.startsWith("health:") -> {
                        val health = trimmed.substringAfter(":").trim().toIntOrNull()
                        result["battery_health"] = mapBatteryHealth(health ?: -1)
                    }
                }
            }
        }

        // Screen refresh rate
        val displayInfo = shell("dumpsys display | grep -E '([0-9]+\\.[0-9]+)\\s*Hz' | head -1")
        if (displayInfo.success) {
            val match = Regex("([0-9]+\\.[0-9]+)\\s*Hz").find(displayInfo.output)
            match?.let { result["screen_refresh_rate"] = "${it.groupValues[1]} Hz" }
        }

        // GPU renderer
        val gpuInfo = shell("dumpsys SurfaceFlinger | grep 'GLES:'")
        if (gpuInfo.success) {
            val gpuLine = gpuInfo.output.substringAfter("GLES:").trim()
            val gpuParts = gpuLine.split(",").map { it.trim() }
            if (gpuParts.size >= 2) {
                result["gpu"] = gpuParts[1]
            } else if (gpuParts.isNotEmpty()) {
                result["gpu"] = gpuParts[0]
            }
        }

        // Camera count
        val cameraInfo = shell("dumpsys media.camera | grep 'Number of camera devices:'")
        if (cameraInfo.success) {
            val count = cameraInfo.output.substringAfter(":").trim().toIntOrNull()
            if (count != null) result["camera_count"] = count.toString()
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
        val q = shellQuote(path)
        val result = shell("ls -la $q")
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
        return adb("pull", shellQuote(remotePath), shellQuote(localPath))
    }

    suspend fun readFilePreview(remotePath: String, maxBytes: Int = 100000): CommandResult {
        val q = shellQuote(remotePath)
        val max = maxBytes.coerceIn(1, 500000)
        return shell("head -c $max $q")
    }

    suspend fun pushFile(localPath: String, remotePath: String): CommandResult {
        return adb("push", shellQuote(localPath), shellQuote(remotePath))
    }

    private suspend fun getRemoteFileSize(remotePath: String): Long {
        val q = shellQuote(remotePath)
        val stat = shell("stat -c %s $q")
        return stat.output.trim().toLongOrNull() ?: -1L
    }

    suspend fun pullFileWithProgress(
        remotePath: String,
        localPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        val total = getRemoteFileSize(remotePath)
        if (total < 0) {
            return@withContext pullFile(remotePath, localPath)
        }
        val file = File(localPath)
        file.parentFile?.mkdirs()

        val deferred = async { pullFile(remotePath, localPath) }
        var lastBytes = 0L
        val job = launch {
            while (deferred.isActive) {
                delay(200)
                val bytes = file.length()
                if (bytes > lastBytes || bytes >= total) {
                    lastBytes = bytes
                    onProgress(bytes, total)
                }
            }
        }
        val result = deferred.await()
        job.cancel()
        onProgress(file.length(), total)
        result
    }

    suspend fun pushFileWithProgress(
        localPath: String,
        remotePath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        val total = File(localPath).length()
        if (total <= 0) {
            return@withContext pushFile(localPath, remotePath)
        }

        val deferred = async { pushFile(localPath, remotePath) }
        val q = shellQuote(remotePath)
        var lastBytes = 0L
        val job = launch {
            while (deferred.isActive) {
                delay(200)
                val sizeResult = shell("stat -c %s $q")
                val bytes = sizeResult.output.trim().toLongOrNull() ?: lastBytes
                if (bytes > lastBytes || bytes >= total) {
                    lastBytes = bytes
                    onProgress(bytes.coerceAtMost(total), total)
                }
            }
        }
        val result = deferred.await()
        job.cancel()
        onProgress(total, total)
        result
    }

    suspend fun deleteFile(path: String): CommandResult {
        return shell("rm -rf ${shellQuote(path)}")
    }

    suspend fun renameFile(oldPath: String, newName: String): CommandResult {
        val parent = oldPath.substringBeforeLast("/").ifEmpty { "/" }
        val newPath = "$parent/$newName"
        return shell("mv ${shellQuote(oldPath)} ${shellQuote(newPath)}")
    }

    suspend fun moveFile(oldPath: String, newPath: String): CommandResult {
        return shell("mv ${shellQuote(oldPath)} ${shellQuote(newPath)}")
    }

    suspend fun copyFile(sourcePath: String, destPath: String): CommandResult {
        return shell("cp -R ${shellQuote(sourcePath)} ${shellQuote(destPath)}")
    }

    suspend fun createDirectory(path: String): CommandResult {
        return shell("mkdir -p ${shellQuote(path)}")
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
        val dump = shell("dumpsys package ${shellQuote(packageName)}")
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
                    trimmed.startsWith("installerPackageName=") -> info["installer"] = trimmed.substringAfter("=")
                }
            }
        }
        return info
    }

    suspend fun getApkPaths(packageName: String): List<String> {
        val result = shell("pm path ${shellQuote(packageName)}")
        if (!result.success) return emptyList()
        return result.output.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
    }

    suspend fun backupApk(packageName: String, destDir: String = "/sdcard/app_backup_adbkit", fileName: String = "${packageName}.apk"): CommandResult {
        val paths = getApkPaths(packageName)
        if (paths.isEmpty()) return CommandResult(success = false, output = "", error = "No APK path found for $packageName", exitCode = 1)
        val mkdirResult = shell("mkdir -p ${shellQuote(destDir)}")
        if (!mkdirResult.success) return mkdirResult
        paths.forEachIndexed { index, path ->
            val outFile = if (paths.size > 1) "$destDir/${packageName}_${index}.apk" else "$destDir/$fileName"
            shell("cp -f ${shellQuote(path)} ${shellQuote(outFile)}")
        }
        return CommandResult(success = true, output = "Backed up to $destDir", error = "", exitCode = 0)
    }

    suspend fun installApp(apkPath: String): CommandResult {
        // Use pm install for device-side paths (after adb push)
        // Use adb install for host-side paths
        return if (apkPath.startsWith("/")) {
            shell("pm install -r ${shellQuote(apkPath)}")
        } else {
            adb("install", "-r", shellQuote(apkPath))
        }
    }

    suspend fun uninstallApp(packageName: String, keepData: Boolean = false): CommandResult {
        return if (keepData) {
            adb("uninstall", "-k", shellQuote(packageName))
        } else {
            adb("uninstall", shellQuote(packageName))
        }
    }

    suspend fun forceStopApp(packageName: String): CommandResult {
        return shell("am force-stop ${shellQuote(packageName)}")
    }

    suspend fun clearAppData(packageName: String): CommandResult {
        return shell("pm clear ${shellQuote(packageName)}")
    }

    suspend fun disableApp(packageName: String): CommandResult {
        return shell("pm disable-user --user 0 ${shellQuote(packageName)}")
    }

    suspend fun enableApp(packageName: String): CommandResult {
        return shell("pm enable ${shellQuote(packageName)}")
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
        return shell("kill -9 ${shellQuote(pid)}")
    }

    suspend fun getProcessDetails(pid: String): Map<String, String> {
        val details = mutableMapOf<String, String>()
        details["pid"] = pid

        val cmdlineResult = shell("cat /proc/$pid/cmdline")
        if (cmdlineResult.success) {
            val cmdline = cmdlineResult.output.replace('\u0000', ' ').trim()
            details["commandLine"] = cmdline.ifEmpty { "(empty)" }
        }

        val statusResult = shell("cat /proc/$pid/status")
        if (statusResult.success) {
            statusResult.output.lines().forEach { line ->
                when {
                    line.startsWith("Threads:") -> details["threads"] = line.substringAfter(":").trim()
                    line.startsWith("PPid:") -> details["ppid"] = line.substringAfter(":").trim()
                    line.startsWith("Name:") -> details["procName"] = line.substringAfter(":").trim()
                }
            }
        }

        val statResult = shell("cat /proc/$pid/stat")
        if (statResult.success) {
            val parts = statResult.output.split(" ")
            if (parts.size > 21) {
                try {
                    val utime = parts[13].toLongOrNull() ?: 0L
                    val stime = parts[14].toLongOrNull() ?: 0L
                    val starttime = parts[21].toLongOrNull() ?: 0L
                    details["cpuTime"] = "${utime + stime}"
                    details["startTime"] = starttime.toString()
                } catch (_: Exception) {}
            }
        }

        val statmResult = shell("cat /proc/$pid/statm")
        if (statmResult.success) {
            val parts = statmResult.output.split(" ")
            if (parts.isNotEmpty()) {
                val pages = parts[0].toLongOrNull() ?: 0L
                details["residentPages"] = "${pages * 4}KB"
            }
        }

        return details
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
        val cap = shell("screencap -p ${shellQuote(remotePath)}")
        if (!cap.success) return cap
        val pull = adb("pull", shellQuote(remotePath), shellQuote(savePath))
        shell("rm ${shellQuote(remotePath)}")
        return pull
    }

    suspend fun startScreenRecord(savePath: String, timeLimit: Int = 180): CommandResult {
        val remotePath = "/sdcard/screenrecord_temp.mp4"
        return shell("screenrecord --time-limit ${shellQuote(timeLimit.toString())} ${shellQuote(remotePath)}")
    }

    suspend fun reboot(mode: String = ""): CommandResult {
        return if (mode.isEmpty()) {
            adb("reboot")
        } else {
            adb("reboot", mode)
        }
    }

    suspend fun fastbootCommand(vararg args: String): CommandResult {
        val cmd = "$fastbootPath ${args.joinToString(" ") { shellQuote(it) }}"
        return executeCommand(cmd)
    }

    suspend fun fastbootFlash(partition: String, imagePath: String): CommandResult {
        return fastbootCommand("flash", partition, imagePath)
    }

    suspend fun fastbootDevices(): CommandResult {
        return executeCommand("$fastbootPath devices")
    }

    suspend fun inputText(text: String): CommandResult {
        return shell("input text ${shellQuote(text)}")
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

    suspend fun getScreenBrightness(): CommandResult {
        return shell("settings get system screen_brightness")
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

    suspend fun getBluetoothStatus(): CommandResult {
        return shell("dumpsys bluetooth_manager | grep -i 'state:' | head -1")
    }

    suspend fun toggleBluetooth(enable: Boolean): CommandResult {
        return shell("svc bluetooth ${if (enable) "enable" else "disable"}")
    }

    suspend fun getAirplaneModeStatus(): CommandResult {
        return shell("settings get global airplane_mode_on")
    }

    suspend fun toggleAirplaneMode(enable: Boolean): CommandResult {
        val value = if (enable) "1" else "0"
        shell("settings put global airplane_mode_on $value")
        return shell("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enable")
    }

    suspend fun openUrl(url: String): CommandResult {
        return shell("am start -a android.intent.action.VIEW -d ${shellQuote(url)}")
    }

    suspend fun launchApp(packageName: String): CommandResult {
        return shell("monkey -p ${shellQuote(packageName)} -c android.intent.category.LAUNCHER 1")
    }

    suspend fun getLogcat(lines: Int = 100, filter: String = ""): CommandResult {
        val cmd = if (filter.isNotEmpty()) {
            "logcat -d -t ${shellQuote(lines.toString())} | grep -i ${shellQuote(filter)}"
        } else {
            "logcat -d -t ${shellQuote(lines.toString())}"
        }
        return shell(cmd)
    }

    suspend fun clearLogcat(): CommandResult {
        return shell("logcat -c")
    }

    suspend fun backupApp(packageName: String, savePath: String): CommandResult {
        val apkPath = shell("pm path ${shellQuote(packageName)}")
        if (!apkPath.success) return apkPath
        val path = apkPath.output.replace("package:", "").trim()
        return adb("pull", shellQuote(path), shellQuote(savePath))
    }

    suspend fun grantPermission(packageName: String, permission: String): CommandResult {
        return shell("pm grant ${shellQuote(packageName)} ${shellQuote(permission)}")
    }

    suspend fun revokePermission(packageName: String, permission: String): CommandResult {
        return shell("pm revoke ${shellQuote(packageName)} ${shellQuote(permission)}")
    }

    suspend fun getAppPermissions(packageName: String): List<String> {
        val result = shell("dumpsys package ${shellQuote(packageName)} | grep permission")
        if (!result.success) return emptyList()
        return result.output.lines()
            .filter { it.contains("android.permission.") }
            .map { it.trim() }
    }

    suspend fun getAppIcon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        val paths = getApkPaths(packageName)
        if (paths.isEmpty()) return@withContext null
        val local = java.io.File(AdbKitApplication.instance.cacheDir, "icon_${packageName}.apk")
        val pull = pullFile(paths.first(), local.absolutePath)
        if (!pull.success) return@withContext null
        val pm = AdbKitApplication.instance.packageManager
        val info = pm.getPackageArchiveInfo(local.absolutePath, 0) ?: return@withContext null
        val ai = info.applicationInfo ?: return@withContext null
        try {
            val drawable = ai.loadIcon(pm)
            drawableToBitmap(drawable)
        } catch (_: Exception) { null }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    suspend fun getAppComponentCounts(packageName: String): Map<String, String> {
        val result = shell("dumpsys package ${shellQuote(packageName)}")
        val counts = mutableMapOf("activities" to "0", "services" to "0", "receivers" to "0", "providers" to "0")
        if (!result.success) return counts
        val sectionMap = mapOf("Activities" to "activities", "Services" to "services", "Receivers" to "receivers", "Providers" to "providers")
        val headerRegex = "^  (Activities|Services|Receivers|Providers):.*".toRegex()
        val entryRegex = "^    [^ ].*".toRegex()
        var current: String? = null
        result.output.lines().forEach { line ->
            val header = headerRegex.find(line)
            if (header != null) {
                current = sectionMap[header.groupValues[1]]
            } else if (current != null) {
                if (entryRegex.matches(line)) {
                    counts[current] = (counts[current]!!.toInt() + 1).toString()
                } else if (!line.startsWith("      ") && line.trim().isNotEmpty() && !line.startsWith("    ")) {
                    current = null
                }
            }
        }
        return counts
    }

    suspend fun setSystemProp(prop: String, value: String): CommandResult {
        return shell("setprop ${shellQuote(prop)} ${shellQuote(value)}")
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

    suspend fun verifyImageMd5(imagePath: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                return@withContext CommandResult(false, "", "File not found: $imagePath", 1)
            }
            val digest = java.security.MessageDigest.getInstance("MD5")
            java.io.FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            val md5 = digest.digest().joinToString("") { "%02x".format(it) }
            CommandResult(true, md5, "", 0)
        } catch (e: Exception) {
            CommandResult(false, "", "MD5 verification failed: ${e.message}", -1)
        }
    }

    suspend fun saveOutputLog(output: String, fileName: String = "adbkit_fastboot_log.txt"): CommandResult = withContext(Dispatchers.IO) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            val file = File(downloadDir, fileName)
            file.writeText(output)
            CommandResult(true, file.absolutePath, "", 0)
        } catch (e: Exception) {
            CommandResult(false, "", "Save log failed: ${e.message}", -1)
        }
    }
}

data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
