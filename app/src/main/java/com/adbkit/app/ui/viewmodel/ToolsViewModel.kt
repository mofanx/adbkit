package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolsUiState(
    val activeDialog: String = "",
    val statusMessage: String = "",
    val commandOutput: String = "",
    val isLoading: Boolean = false
)

class ToolsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun executeTool(action: String) {
        when (action) {
            "screenshot" -> takeScreenshot()
            "screenrecord" -> startScreenRecord()
            "install" -> _uiState.update { it.copy(statusMessage = "Please select APK via file manager") }
            "reboot" -> _uiState.update { it.copy(activeDialog = "reboot") }
            "input_text" -> _uiState.update { it.copy(activeDialog = "input_text") }
            "key_event" -> _uiState.update { it.copy(activeDialog = "key_event") }
            "brightness" -> _uiState.update { it.copy(activeDialog = "brightness") }
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

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = "") }
    }

    private fun takeScreenshot() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Taking screenshot...") }
            val result = AdbService.shell("screencap -p /sdcard/screenshot_adbkit.png")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Screenshot saved to /sdcard/screenshot_adbkit.png" else "Screenshot failed: ${result.error}")
            }
        }
    }

    private fun startScreenRecord() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Recording (max 3min)...") }
            val result = AdbService.shell("screenrecord --time-limit 180 /sdcard/screenrecord_adbkit.mp4 &")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Recording... File: /sdcard/screenrecord_adbkit.mp4" else "Record failed: ${result.error}")
            }
        }
    }

    fun reboot(mode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "", statusMessage = "Rebooting...") }
            val result = AdbService.reboot(mode)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Reboot command sent" else "Reboot failed: ${result.error}")
            }
        }
    }

    fun inputText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.inputText(text)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Text sent" else "Input failed: ${result.error}")
            }
        }
    }

    fun sendKeyEvent(code: Int) {
        viewModelScope.launch {
            val result = AdbService.inputKeyEvent(code)
            if (!result.success) {
                _uiState.update { it.copy(statusMessage = "Key event failed: ${result.error}") }
            }
        }
    }

    fun setBrightness(value: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.setScreenBrightness(value)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Brightness set to $value" else "Set failed: ${result.error}")
            }
        }
    }

    private fun setScreenTimeout() {
        viewModelScope.launch {
            val result = AdbService.setScreenTimeout(300000) // 5 minutes
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Screen timeout set to 5min" else "Set failed: ${result.error}")
            }
        }
    }

    private fun toggleWifi() {
        viewModelScope.launch {
            val status = AdbService.getWifiStatus()
            val enable = status.output.contains("disabled", ignoreCase = true)
            val result = AdbService.toggleWifi(enable)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "WiFi ${if (enable) "ON" else "OFF"}" else "Failed: ${result.error}")
            }
        }
    }

    private fun toggleBluetooth() {
        viewModelScope.launch {
            val result = AdbService.toggleBluetooth(true)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Bluetooth toggled" else "Failed: ${result.error}")
            }
        }
    }

    private fun toggleAirplane() {
        viewModelScope.launch {
            val result = AdbService.toggleAirplaneMode(true)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Airplane mode toggled" else "Failed: ${result.error}")
            }
        }
    }

    fun openUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.openUrl(url)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "URL opened" else "Open failed: ${result.error}")
            }
        }
    }

    fun launchApp(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.launchApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "App launched" else "Launch failed: ${result.error}")
            }
        }
    }

    private fun showCurrentActivity() {
        viewModelScope.launch {
            val result = AdbService.dumpActivity()
            _uiState.update {
                it.copy(statusMessage = if (result.success) result.output else "Failed: ${result.error}")
            }
        }
    }

    private fun showLogcat() {
        viewModelScope.launch {
            val result = AdbService.getLogcat(200)
            _uiState.update {
                it.copy(
                    activeDialog = "logcat_view",
                    commandOutput = if (result.success) result.output else "Failed: ${result.error}"
                )
            }
        }
    }

    fun clearLogcat() {
        viewModelScope.launch {
            AdbService.clearLogcat()
            _uiState.update { it.copy(commandOutput = "Log cleared") }
        }
    }

    private fun showSystemProps() {
        viewModelScope.launch {
            val result = AdbService.shell("getprop")
            _uiState.update {
                it.copy(
                    activeDialog = "logcat_view",
                    commandOutput = if (result.success) result.output else "Failed: ${result.error}"
                )
            }
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
                it.copy(statusMessage = if (result.success) "Density changed" else "Change failed: ${result.error}")
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
                it.copy(statusMessage = if (result.success) "Resolution changed" else "Change failed: ${result.error}")
            }
        }
    }

    private fun toggleNavBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.navigation=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Navigation bar hidden" else "Failed: ${result.error}")
            }
        }
    }

    private fun toggleStatusBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.status=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Status bar hidden" else "Failed: ${result.error}")
            }
        }
    }
}
