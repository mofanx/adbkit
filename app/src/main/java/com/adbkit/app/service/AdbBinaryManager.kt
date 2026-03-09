package com.adbkit.app.service

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AdbBinaryManager {

    private const val ADB_BINARY_NAME = "adb"
    private const val FASTBOOT_BINARY_NAME = "fastboot"

    private fun getBinDir(context: Context): File {
        val dir = File(context.filesDir, "bin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    suspend fun setup(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        val adbPath = setupBinary(context, ADB_BINARY_NAME)
        val fastbootPath = setupBinary(context, FASTBOOT_BINARY_NAME)
        Pair(adbPath, fastbootPath)
    }

    private fun setupBinary(context: Context, binaryName: String): String {
        // 1. Check if already extracted and up-to-date
        val binDir = getBinDir(context)
        val extractedBinary = File(binDir, binaryName)
        val versionFile = File(binDir, "${binaryName}.version")
        val currentVersion = getAssetVersion(context, binaryName)

        if (extractedBinary.exists() && extractedBinary.canExecute()) {
            val savedVersion = if (versionFile.exists()) versionFile.readText().trim() else ""
            if (savedVersion == currentVersion) {
                return extractedBinary.absolutePath
            }
        }

        // 2. Try to extract from assets
        val extracted = extractFromAssets(context, binaryName, extractedBinary)
        if (extracted) {
            versionFile.writeText(currentVersion)
            return extractedBinary.absolutePath
        }

        // 3. Check system paths as fallback
        val systemPaths = listOf(
            "/system/bin/$binaryName",
            "/system/xbin/$binaryName",
            "/sbin/$binaryName",
            "/vendor/bin/$binaryName",
            "/data/local/tmp/$binaryName"
        )
        for (path in systemPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return path
            }
        }

        // 4. Fall back to bare name (hope it's in PATH)
        return binaryName
    }

    private fun getAssetVersion(context: Context, binaryName: String): String {
        return try {
            val abi = getAbi()
            val assets = context.assets.list("bin/$abi") ?: emptyArray()
            if (assets.contains(binaryName)) {
                // Use APK last modified time as version
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .lastUpdateTime.toString()
            } else ""
        } catch (e: Exception) { "" }
    }

    private fun extractFromAssets(context: Context, binaryName: String, targetFile: File): Boolean {
        val abi = getAbi()
        val assetPath = "bin/$abi/$binaryName"

        return try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)
            true
        } catch (e: Exception) {
            // Asset not found - try generic path
            try {
                context.assets.open("bin/$binaryName").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun getStatus(context: Context): String {
        val binDir = getBinDir(context)
        val adbFile = File(binDir, ADB_BINARY_NAME)
        val abi = getAbi()
        return buildString {
            appendLine("ABI: $abi")
            appendLine("Bin dir: ${binDir.absolutePath}")
            if (adbFile.exists()) {
                appendLine("ADB: ${adbFile.absolutePath} (${if (adbFile.canExecute()) "executable" else "not executable"})")
                appendLine("Size: ${adbFile.length()} bytes")
            } else {
                appendLine("ADB: not extracted")
                // Check assets
                try {
                    val assets = context.assets.list("bin/$abi")
                    appendLine("Assets (bin/$abi): ${assets?.joinToString() ?: "empty"}")
                } catch (e: Exception) {
                    appendLine("Assets: cannot read")
                }
            }
        }
    }
}
