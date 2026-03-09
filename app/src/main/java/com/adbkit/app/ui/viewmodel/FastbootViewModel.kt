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
    val imagePath: String = "",
    val selectedPartition: String = "recovery",
    val customCommand: String = "",
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
            val hasDevice = result.success && result.output.isNotBlank() && result.output.contains("fastboot")
            _uiState.update { it.copy(deviceDetected = hasDevice) }
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
            val fullCmd = if (cmd.startsWith("fastboot")) cmd else "fastboot $cmd"
            val result = AdbService.executeCommand(fullCmd)
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
