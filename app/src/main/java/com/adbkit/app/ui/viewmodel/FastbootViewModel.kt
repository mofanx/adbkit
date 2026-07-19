package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FastbootUiState(
    val deviceDetected: Boolean = false,
    val hasRootAccess: Boolean = false,
    val permissionWarning: String = "",
    val imagePath: String = "",
    val selectedPartition: String = "recovery",
    val customCommand: String = "",
    val expectedMd5: String = "",
    val imageMd5: String = "",
    val partitionList: List<String> = emptyList(),
    val selectedBackupPartition: String = "",
    val backupDestination: String = "/sdcard/adbkit_partition_backup",
    val restoreImagePath: String = "",
    val output: String = "",
    val isExecuting: Boolean = false
)

class FastbootViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FastbootUiState())
    val uiState: StateFlow<FastbootUiState> = _uiState.asStateFlow()

    init {
        checkDevices()
    }

    fun checkDevices() {
        viewModelScope.launch {
            val result = AdbService.fastbootDevices()
            val rawOutput = result.output + "\n" + result.error
            val hasDevice = result.success && result.output.isNotBlank() && result.output.contains("fastboot")
            val noPermissions = rawOutput.lowercase().contains("no permissions") ||
                rawOutput.lowercase().contains("permission denied") ||
                rawOutput.lowercase().contains("insufficient permissions")
            val root = AdbService.hasRootAccess()
            _uiState.update {
                it.copy(
                    deviceDetected = hasDevice,
                    hasRootAccess = root,
                    permissionWarning = when {
                        noPermissions && !root -> "fastboot_no_permission"
                        !hasDevice -> "fastboot_no_device"
                        else -> ""
                    }
                )
            }
            appendOutput(if (hasDevice) "Fastboot device detected" else "No Fastboot device found")
        }
    }

    fun setImagePath(path: String) {
        _uiState.update { it.copy(imagePath = path) }
    }

    fun setPartition(partition: String) {
        _uiState.update { it.copy(selectedPartition = partition) }
    }

    fun setCustomCommand(cmd: String) {
        _uiState.update { it.copy(customCommand = cmd) }
    }

    fun setExpectedMd5(md5: String) {
        _uiState.update { it.copy(expectedMd5 = md5.trim()) }
    }

    fun verifyImageMd5() {
        val path = _uiState.value.imagePath
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val result = AdbService.verifyImageMd5(path)
            val computed = if (result.success) result.output.trim() else ""
            val expected = _uiState.value.expectedMd5
            val message = if (result.success) {
                if (expected.isNotBlank() && !computed.equals(expected, ignoreCase = true)) {
                    "MD5 mismatch! Expected $expected, got $computed"
                } else {
                    "MD5: $computed"
                }
            } else {
                "MD5 verification failed: ${result.error}"
            }
            appendOutput(message)
            _uiState.update { it.copy(imageMd5 = computed, isExecuting = false) }
        }
    }

    fun saveFlashLog() {
        val log = _uiState.value.output
        if (log.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val result = AdbService.saveOutputLog(log, "adbkit_fastboot_log.txt")
            appendOutput(if (result.success) "Log saved: ${result.output}" else "Save log failed: ${result.error}")
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun loadPartitions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            val partitions = AdbService.listPartitions()
            val first = partitions.firstOrNull() ?: ""
            _uiState.update {
                it.copy(
                    partitionList = partitions,
                    selectedBackupPartition = if (it.selectedBackupPartition.isEmpty()) first else it.selectedBackupPartition,
                    isExecuting = false
                )
            }
            appendOutput(if (partitions.isNotEmpty()) "Found ${partitions.size} partitions" else "No partitions found (root may be required)")
        }
    }

    fun setBackupPartition(partition: String) {
        _uiState.update { it.copy(selectedBackupPartition = partition) }
    }

    fun setBackupDestination(dest: String) {
        _uiState.update { it.copy(backupDestination = dest) }
    }

    fun backupSelectedPartition() {
        val partition = _uiState.value.selectedBackupPartition
        if (partition.isBlank()) {
            appendOutput("Please select or enter a partition name")
            return
        }
        viewModelScope.launch {
            appendOutput("$ adb shell dd if=/dev/block/bootdevice/by-name/$partition of=${_uiState.value.backupDestination}/${partition}.img")
            _uiState.update { it.copy(isExecuting = true) }
            val result = AdbService.backupPartition(partition, _uiState.value.backupDestination)
            appendOutput(if (result.success) "Backup complete: ${result.output}" else "Backup failed: ${result.error}")
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun setRestoreImagePath(path: String) {
        _uiState.update { it.copy(restoreImagePath = path) }
    }

    fun restoreSelectedPartition() {
        val partition = _uiState.value.selectedBackupPartition
        val imagePath = _uiState.value.restoreImagePath
        if (partition.isBlank() || imagePath.isBlank()) {
            appendOutput("Please select a partition and enter the device-side image path to restore")
            return
        }
        viewModelScope.launch {
            appendOutput("$ adb shell dd if=$imagePath of=/dev/block/bootdevice/by-name/$partition")
            _uiState.update { it.copy(isExecuting = true) }
            val result = AdbService.restorePartition(partition, imagePath)
            appendOutput(if (result.success) "Restore complete: ${result.output}" else "Restore failed: ${result.error}")
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun flashImage() {
        val path = _uiState.value.imagePath
        val partition = _uiState.value.selectedPartition
        if (path.isBlank()) return

        viewModelScope.launch {
            appendOutput("$ fastboot flash $partition $path")
            _uiState.update { it.copy(isExecuting = true) }
            val result = AdbService.fastbootFlash(partition, path)
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput("Error: ${result.error}")
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun fastbootReboot(mode: String) {
        viewModelScope.launch {
            val cmd = if (mode.isEmpty()) "reboot" else "reboot-$mode"
            appendOutput("$ fastboot $cmd")
            val result = AdbService.fastbootCommand(cmd)
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput("Error: ${result.error}")
        }
    }

    fun unlockBootloader() {
        viewModelScope.launch {
            appendOutput("$ fastboot flashing unlock")
            val result = AdbService.fastbootCommand("flashing", "unlock")
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput("Error: ${result.error}")
        }
    }

    fun lockBootloader() {
        viewModelScope.launch {
            appendOutput("$ fastboot flashing lock")
            val result = AdbService.fastbootCommand("flashing", "lock")
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput("Error: ${result.error}")
        }
    }

    fun erasePartition() {
        val partition = _uiState.value.selectedPartition
        viewModelScope.launch {
            appendOutput("$ fastboot erase $partition")
            val result = AdbService.fastbootCommand("erase", partition)
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput("Error: ${result.error}")
        }
    }

    fun getVar() {
        viewModelScope.launch {
            appendOutput("$ fastboot getvar all")
            val result = AdbService.fastbootCommand("getvar", "all")
            appendOutput(result.output)
            if (result.error.isNotEmpty()) {
                // fastboot getvar often writes to stderr
                appendOutput(result.error)
            }
        }
    }

    fun executeCustomCommand() {
        val cmd = _uiState.value.customCommand.trim()
        if (cmd.isBlank()) return

        viewModelScope.launch {
            appendOutput("$ $cmd")
            _uiState.update { it.copy(customCommand = "", isExecuting = true) }
            val fbPath = AdbService.getFastbootPath()
            val args = cmd.split("\\s+".toRegex()).filter { it.isNotBlank() }.toMutableList()
            if (args.firstOrNull()?.equals("fastboot", ignoreCase = true) == true) {
                args[0] = fbPath
            } else {
                args.add(0, fbPath)
            }
            val result = AdbService.executeCommand(args)
            appendOutput(result.output)
            if (result.error.isNotEmpty()) appendOutput(result.error)
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "") }
    }

    private fun appendOutput(text: String) {
        if (text.isBlank()) return
        _uiState.update {
            it.copy(output = if (it.output.isEmpty()) text else "${it.output}\n$text")
        }
    }
}
