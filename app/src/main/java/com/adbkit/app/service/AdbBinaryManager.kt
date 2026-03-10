package com.adbkit.app.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object AdbBinaryManager {

    private const val TAG = "AdbBinaryManager"

    // Binaries are packaged as .so in jniLibs/ so Android extracts them
    // to nativeLibraryDir which has execute permission (unlike filesDir).
    private const val ADB_LIB_NAME = "libadb.so"
    private const val FASTBOOT_LIB_NAME = "libfastboot.so"

    private val _adbReady = MutableStateFlow(false)
    val adbReady: StateFlow<Boolean> = _adbReady.asStateFlow()

    var lastError: String = ""
        private set

    private fun getNativeLibDir(context: Context): String {
        return context.applicationInfo.nativeLibraryDir
    }

    suspend fun setup(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        lastError = ""
        val adbPath = findBinary(context, "adb", ADB_LIB_NAME)
        val fastbootPath = findBinary(context, "fastboot", FASTBOOT_LIB_NAME)

        // Verify adb is actually executable at runtime
        val ready = verifyBinary(adbPath)
        _adbReady.value = ready

        Log.i(TAG, "Setup complete: adb=$adbPath (ready=$ready), fastboot=$fastbootPath")
        if (!ready) {
            Log.e(TAG, "ADB NOT READY: $lastError")
        }
        Pair(adbPath, fastbootPath)
    }

    private fun findBinary(context: Context, binaryName: String, libName: String): String {
        // 1. Check nativeLibraryDir (jniLibs packaging — has exec permission)
        val nativeDir = getNativeLibDir(context)
        val nativeFile = File(nativeDir, libName)
        if (nativeFile.exists() && nativeFile.canExecute()) {
            Log.i(TAG, "$binaryName: found in nativeLibraryDir: ${nativeFile.absolutePath} (${nativeFile.length()} bytes)")
            return nativeFile.absolutePath
        } else if (nativeFile.exists()) {
            Log.w(TAG, "$binaryName: found in nativeLibraryDir but not executable: ${nativeFile.absolutePath}")
        } else {
            Log.w(TAG, "$binaryName: not found in nativeLibraryDir: ${nativeFile.absolutePath}")
        }

        // 2. Check system paths as fallback (for rooted devices or manual placement)
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
        return binaryName
    }

    /**
     * Actually try to run the binary to verify it works.
     * File.canExecute() can return true even on noexec filesystems.
     */
    private fun verifyBinary(adbPath: String): Boolean {
        return try {
            val proc = ProcessBuilder(adbPath, "version")
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            val exit = proc.waitFor()
            if (exit == 0 && output.contains("Android Debug Bridge")) {
                Log.i(TAG, "ADB verify OK: ${output.lines().firstOrNull()}")
                true
            } else {
                lastError = "ADB binary returned exit=$exit: ${output.take(200)}"
                Log.e(TAG, lastError)
                false
            }
        } catch (e: Exception) {
            lastError = "ADB binary not executable: ${e.message}"
            Log.e(TAG, lastError, e)
            false
        }
    }

    fun getStatus(context: Context): String {
        val nativeDir = getNativeLibDir(context)
        val adbFile = File(nativeDir, ADB_LIB_NAME)
        val fastbootFile = File(nativeDir, FASTBOOT_LIB_NAME)
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        return buildString {
            appendLine("ABI: $abi")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Native lib dir: $nativeDir")
            appendLine("ADB ready: ${_adbReady.value}")
            if (lastError.isNotEmpty()) appendLine("Last error: $lastError")
            appendLine("Current ADB path: ${AdbService.getAdbPath()}")
            appendLine("")
            appendLine("=== Bundled binaries (jniLibs) ===")
            appendLine("ADB ($ADB_LIB_NAME):")
            if (adbFile.exists()) {
                appendLine("  path: ${adbFile.absolutePath}")
                appendLine("  size: ${adbFile.length()} bytes")
                appendLine("  canExecute: ${adbFile.canExecute()}")
            } else {
                appendLine("  NOT FOUND in nativeLibraryDir")
            }
            appendLine("Fastboot ($FASTBOOT_LIB_NAME):")
            if (fastbootFile.exists()) {
                appendLine("  path: ${fastbootFile.absolutePath}")
                appendLine("  size: ${fastbootFile.length()} bytes")
                appendLine("  canExecute: ${fastbootFile.canExecute()}")
            } else {
                appendLine("  NOT FOUND in nativeLibraryDir")
            }
            appendLine("")
            appendLine("=== System fallback paths ===")
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
