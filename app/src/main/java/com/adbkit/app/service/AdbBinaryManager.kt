package com.adbkit.app.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AdbBinaryManager {

    private const val TAG = "AdbBinaryManager"
    private const val ADB_BINARY_NAME = "adb"
    private const val FASTBOOT_BINARY_NAME = "fastboot"

    var adbReady: Boolean = false
        private set
    var lastError: String = ""
        private set

    private fun getBinDir(context: Context): File {
        val dir = File(context.filesDir, "bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    suspend fun setup(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        lastError = ""
        val adbPath = setupBinary(context, ADB_BINARY_NAME)
        val fastbootPath = setupBinary(context, FASTBOOT_BINARY_NAME)

        // Verify adb is actually usable
        adbReady = if (adbPath != ADB_BINARY_NAME) {
            val file = File(adbPath)
            val exists = file.exists()
            val canExec = file.canExecute()
            val sizeOk = file.length() > 100_000
            Log.i(TAG, "ADB verify: path=$adbPath exists=$exists exec=$canExec size=${file.length()}")
            if (!exists) lastError = "ADB binary not found at $adbPath"
            else if (!canExec) lastError = "ADB binary not executable: $adbPath"
            else if (!sizeOk) lastError = "ADB binary too small (${file.length()} bytes), likely corrupted"
            exists && canExec && sizeOk
        } else {
            // Bare name fallback - test if adb is in PATH
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which adb"))
                val exit = proc.waitFor()
                if (exit == 0) {
                    Log.i(TAG, "ADB found in PATH")
                    true
                } else {
                    lastError = "ADB binary not bundled and not found in system PATH"
                    Log.e(TAG, lastError)
                    false
                }
            } catch (e: Exception) {
                lastError = "ADB binary not available: ${e.message}"
                Log.e(TAG, lastError)
                false
            }
        }

        Log.i(TAG, "Setup complete: adb=$adbPath (ready=$adbReady), fastboot=$fastbootPath")
        Pair(adbPath, fastbootPath)
    }

    private fun setupBinary(context: Context, binaryName: String): String {
        // 1. Check if already extracted and up-to-date
        val binDir = getBinDir(context)
        val extractedBinary = File(binDir, binaryName)
        val versionFile = File(binDir, "${binaryName}.version")
        val currentVersion = getAssetVersion(context, binaryName)

        if (extractedBinary.exists() && extractedBinary.canExecute() && extractedBinary.length() > 100_000) {
            val savedVersion = if (versionFile.exists()) versionFile.readText().trim() else ""
            if (savedVersion == currentVersion) {
                Log.i(TAG, "$binaryName: using cached ${extractedBinary.absolutePath}")
                return extractedBinary.absolutePath
            }
        }

        // 2. Try to extract from assets
        val extracted = extractFromAssets(context, binaryName, extractedBinary)
        if (extracted && extractedBinary.length() > 100_000) {
            versionFile.writeText(currentVersion)
            Log.i(TAG, "$binaryName: extracted from assets to ${extractedBinary.absolutePath}")
            return extractedBinary.absolutePath
        } else if (extracted) {
            Log.w(TAG, "$binaryName: extracted but too small (${extractedBinary.length()} bytes)")
            extractedBinary.delete()
        }

        // 3. Check system paths as fallback
        val systemPaths = listOf(
            "/data/local/tmp/$binaryName",
            "/system/bin/$binaryName",
            "/system/xbin/$binaryName",
            "/sbin/$binaryName",
            "/vendor/bin/$binaryName"
        )
        for (path in systemPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                Log.i(TAG, "$binaryName: found system binary at $path")
                return path
            }
        }

        Log.w(TAG, "$binaryName: not found anywhere, falling back to bare name")
        // 4. Fall back to bare name (hope it's in PATH)
        return binaryName
    }

    private fun getAssetVersion(context: Context, binaryName: String): String {
        return try {
            val abi = getAbi()
            val assets = context.assets.list("bin/$abi") ?: emptyArray()
            if (assets.contains(binaryName)) {
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .lastUpdateTime.toString()
            } else ""
        } catch (e: Exception) { "" }
    }

    private fun extractFromAssets(context: Context, binaryName: String, targetFile: File): Boolean {
        val abi = getAbi()
        // Try ABI-specific path first, then generic
        val paths = listOf("bin/$abi/$binaryName", "bin/$binaryName")

        for (assetPath in paths) {
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                Log.i(TAG, "Extracted $assetPath -> ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Asset not found: $assetPath")
            }
        }
        return false
    }

    fun getStatus(context: Context): String {
        val binDir = getBinDir(context)
        val adbFile = File(binDir, ADB_BINARY_NAME)
        val abi = getAbi()
        return buildString {
            appendLine("ABI: $abi")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Bin dir: ${binDir.absolutePath}")
            appendLine("ADB ready: $adbReady")
            if (lastError.isNotEmpty()) appendLine("Last error: $lastError")
            appendLine("Current ADB path: ${AdbService.getAdbPath()}")
            if (adbFile.exists()) {
                appendLine("Extracted ADB: ${adbFile.absolutePath}")
                appendLine("  executable: ${adbFile.canExecute()}")
                appendLine("  size: ${adbFile.length()} bytes")
            } else {
                appendLine("Extracted ADB: not found")
            }
            // Check assets
            try {
                val abiAssets = context.assets.list("bin/$abi")
                appendLine("Assets (bin/$abi): ${abiAssets?.joinToString() ?: "empty"}")
                val genericAssets = context.assets.list("bin")
                appendLine("Assets (bin/): ${genericAssets?.joinToString() ?: "empty"}")
            } catch (e: Exception) {
                appendLine("Assets: cannot read (${e.message})")
            }
            // Check system paths
            appendLine("System ADB search:")
            listOf("/data/local/tmp/adb", "/system/bin/adb", "/system/xbin/adb").forEach { path ->
                val f = File(path)
                val status = when {
                    !f.exists() -> "not found"
                    !f.canExecute() -> "exists but not executable"
                    else -> "OK (${f.length()} bytes)"
                }
                appendLine("  $path: $status")
            }
        }
    }
}
