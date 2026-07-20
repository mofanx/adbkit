package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolsUiState(
    val activeDialog: String = "",
    val statusMessage: String = "",
    val commandOutput: String = "",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val lastScreenshotPath: String = "",
    val currentBrightness: String = ""
)

class ToolsViewModel : LocalizedViewModel() {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()
    private var screenRecordJob: Job? = null

    fun executeTool(action: String) {
        when (action) {
            "screenshot" -> takeScreenshot()
            "screenrecord" -> startScreenRecord()
            "install" -> _uiState.update { it.copy(statusMessage = strings.pleaseSelectApk) }
            "reboot" -> _uiState.update { it.copy(activeDialog = "reboot") }
            "input_text" -> _uiState.update { it.copy(activeDialog = "input_text") }
            "key_event" -> _uiState.update { it.copy(activeDialog = "key_event") }
            "brightness" -> loadBrightnessAndShowDialog()
            "screen_timeout" -> setScreenTimeout()
            "wifi" -> toggleWifi()
            "bluetooth" -> toggleBluetooth()
            "airplane" -> toggleAirplane()
            "open_url" -> _uiState.update { it.copy(activeDialog = "open_url") }
            "launch_app" -> _uiState.update { it.copy(activeDialog = "launch_app") }
            "current_activity" -> showCurrentActivity()
            "logcat" -> showLogcat()
            "sysprop" -> showSystemProps()
            "density" -> _uiState.update { it.copy(activeDialog = "density") }
            "resolution" -> _uiState.update { it.copy(activeDialog = "resolution") }
            "navbar" -> toggleNavBar()
            "statusbar" -> toggleStatusBar()
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = "") }
    }

    private fun loadBrightnessAndShowDialog() {
        viewModelScope.launch {
            val result = AdbService.getScreenBrightness()
            val current = if (result.success) result.output.trim() else ""
            _uiState.update { it.copy(activeDialog = "brightness", currentBrightness = current) }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = "") }
    }

    private fun takeScreenshot() {
        viewModelScope.launch {
            val root = AdbService.getDeviceExternalStorageRoot()
            val path = "$root/screenshot_adbkit.png"
            _uiState.update { it.copy(statusMessage = strings.takingScreenshot) }
            val result = AdbService.shell("screencap -p ${AdbService.shellQuote(path)}")
            _uiState.update {
                it.copy(
                    statusMessage = if (result.success) strings.screenshotSaved(path) else strings.screenshotFailed(result.error),
                    lastScreenshotPath = if (result.success) path else ""
                )
            }
        }
    }

    private fun startScreenRecord() {
        if (screenRecordJob?.isActive == true) return
        screenRecordJob?.cancel()
        screenRecordJob = viewModelScope.launch {
            val root = AdbService.getDeviceExternalStorageRoot()
            val path = "$root/screenrecord_adbkit.mp4"
            try {
                _uiState.update { it.copy(isRecording = true, statusMessage = strings.recordingFile(path)) }
                val result = AdbService.shell("screenrecord --time-limit 180 ${AdbService.shellQuote(path)}")
                if (result.success || result.error.contains("killed", ignoreCase = true)) {
                    _uiState.update { it.copy(isRecording = false, statusMessage = strings.recordingSaved(path)) }
                } else {
                    _uiState.update { it.copy(isRecording = false, statusMessage = strings.recordFailed(result.error)) }
                }
            } catch (e: CancellationException) {
                stopScreenRecordProcess()
            }
        }
    }

    fun stopScreenRecord() {
        screenRecordJob?.cancel()
        screenRecordJob = null
        viewModelScope.launch {
            stopScreenRecordProcess()
            _uiState.update { it.copy(isRecording = false, statusMessage = strings.recordingStopped) }
        }
    }

    private suspend fun stopScreenRecordProcess() {
        // Send SIGINT to any screenrecord process on the device
        AdbService.shell("pkill -2 screenrecord 2>/dev/null || kill -2 \$(pidof screenrecord) 2>/dev/null || true")
        // Give the process a moment to flush and stop
        AdbService.shell("sleep 1")
    }

    fun reboot(mode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "", statusMessage = strings.rebooting) }
            val result = AdbService.reboot(mode)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.rebootCommandSent else strings.rebootFailed(result.error))
            }
        }
    }

    fun inputText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.inputText(text)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.textSent else strings.inputFailed(result.error))
            }
        }
    }

    fun sendKeyEvent(code: Int) {
        viewModelScope.launch {
            val result = AdbService.inputKeyEvent(code)
            if (!result.success) {
                _uiState.update { it.copy(statusMessage = strings.keyEventFailed(result.error)) }
            }
        }
    }

    fun setBrightness(value: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.setScreenBrightness(value)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.brightnessSet(value) else strings.setFailed(result.error))
            }
        }
    }

    private fun setScreenTimeout() {
        viewModelScope.launch {
            val result = AdbService.setScreenTimeout(300000) // 5 minutes
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.screenTimeoutSet else strings.setFailed(result.error))
            }
        }
    }

    private fun toggleWifi() {
        viewModelScope.launch {
            val status = AdbService.getWifiStatus()
            val enable = status.output.contains("disabled", ignoreCase = true)
            val result = AdbService.toggleWifi(enable)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.toggleState(strings.toolWifiToggle, if (enable) strings.on else strings.off) else strings.operationFailed(result.error))
            }
        }
    }

    private fun toggleBluetooth() {
        viewModelScope.launch {
            val status = AdbService.getBluetoothStatus()
            val currentlyOn = !status.output.contains("OFF", ignoreCase = true) && status.output.contains("ON", ignoreCase = true)
            val result = AdbService.toggleBluetooth(!currentlyOn)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.toggleState(strings.toolBluetoothToggle, if (!currentlyOn) strings.on else strings.off) else strings.operationFailed(result.error))
            }
        }
    }

    private fun toggleAirplane() {
        viewModelScope.launch {
            val status = AdbService.getAirplaneModeStatus()
            val currentlyOn = status.output.trim() == "1"
            val result = AdbService.toggleAirplaneMode(!currentlyOn)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.toggleState(strings.toolAirplaneMode, if (!currentlyOn) strings.on else strings.off) else strings.operationFailed(result.error))
            }
        }
    }

    fun openUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.openUrl(url)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.urlOpened else strings.openUrlFailed(result.error))
            }
        }
    }

    fun launchApp(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.launchApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.appLaunched(pkg) else strings.launchFailed(result.error))
            }
        }
    }

    private fun showCurrentActivity() {
        viewModelScope.launch {
            val result = AdbService.dumpActivity()
            _uiState.update {
                it.copy(statusMessage = if (result.success) result.output else strings.currentActivityFailed(result.error))
            }
        }
    }

    private fun showLogcat() {
        viewModelScope.launch {
            val result = AdbService.getLogcat(200)
            _uiState.update {
                it.copy(
                    activeDialog = "logcat_view",
                    commandOutput = if (result.success) result.output else strings.operationFailed(result.error)
                )
            }
        }
    }

    fun clearLogcat() {
        viewModelScope.launch {
            AdbService.clearLogcat()
            _uiState.update { it.copy(commandOutput = strings.logCleared) }
        }
    }

    private fun showSystemProps() {
        viewModelScope.launch {
            val result = AdbService.shell("getprop")
            _uiState.update {
                it.copy(
                    activeDialog = "sysprop_view",
                    commandOutput = if (result.success) result.output else strings.operationFailed(result.error)
                )
            }
        }
    }

    fun setSystemProp(name: String, value: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = strings.settingProperty(name)) }
            val result = AdbService.setSystemProp(name, value)
            _uiState.update {
                it.copy(
                    statusMessage = if (result.success) strings.propertySet(name) else strings.setFailed(result.error)
                )
            }
            if (result.success) showSystemProps()
        }
    }

    fun setDensity(dpi: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = if (dpi == "reset") {
                AdbService.shell("wm density reset")
            } else {
                AdbService.shell("wm density $dpi")
            }
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.densityChanged else strings.changeFailed(result.error))
            }
        }
    }

    fun setResolution(res: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = if (res == "reset") {
                AdbService.shell("wm size reset")
            } else {
                AdbService.shell("wm size $res")
            }
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.resolutionChanged else strings.changeFailed(result.error))
            }
        }
    }

    private fun toggleNavBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.navigation=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.toggleState(strings.toolNavBar, strings.hidden) else strings.operationFailed(result.error))
            }
        }
    }

    private fun toggleStatusBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.status=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.toggleState(strings.toolStatusBar, strings.hidden) else strings.operationFailed(result.error))
            }
        }
    }
}
