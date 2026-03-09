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
            "install" -> _uiState.update { it.copy(statusMessage = "请通过文件管理器选择APK文件安装") }
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
            _uiState.update { it.copy(statusMessage = "正在截图...") }
            val result = AdbService.shell("screencap -p /sdcard/screenshot_adbkit.png")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "截图已保存到 /sdcard/screenshot_adbkit.png" else "截图失败: ${result.error}")
            }
        }
    }

    private fun startScreenRecord() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "开始录屏 (最长3分钟)...") }
            val result = AdbService.shell("screenrecord --time-limit 180 /sdcard/screenrecord_adbkit.mp4 &")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "录屏中... 文件: /sdcard/screenrecord_adbkit.mp4" else "录屏失败: ${result.error}")
            }
        }
    }

    fun reboot(mode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "", statusMessage = "正在重启...") }
            val result = AdbService.reboot(mode)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "重启命令已发送" else "重启失败: ${result.error}")
            }
        }
    }

    fun inputText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.inputText(text)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "文本已输入" else "输入失败: ${result.error}")
            }
        }
    }

    fun sendKeyEvent(code: Int) {
        viewModelScope.launch {
            val result = AdbService.inputKeyEvent(code)
            if (!result.success) {
                _uiState.update { it.copy(statusMessage = "按键失败: ${result.error}") }
            }
        }
    }

    fun setBrightness(value: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.setScreenBrightness(value)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "亮度已设置为 $value" else "设置失败: ${result.error}")
            }
        }
    }

    private fun setScreenTimeout() {
        viewModelScope.launch {
            val result = AdbService.setScreenTimeout(300000) // 5 minutes
            _uiState.update {
                it.copy(statusMessage = if (result.success) "屏幕超时已设为5分钟" else "设置失败: ${result.error}")
            }
        }
    }

    private fun toggleWifi() {
        viewModelScope.launch {
            val status = AdbService.getWifiStatus()
            val enable = status.output.contains("disabled", ignoreCase = true)
            val result = AdbService.toggleWifi(enable)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "WiFi已${if (enable) "开启" else "关闭"}" else "操作失败: ${result.error}")
            }
        }
    }

    private fun toggleBluetooth() {
        viewModelScope.launch {
            val result = AdbService.toggleBluetooth(true)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "蓝牙操作已执行" else "操作失败: ${result.error}")
            }
        }
    }

    private fun toggleAirplane() {
        viewModelScope.launch {
            val result = AdbService.toggleAirplaneMode(true)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "飞行模式操作已执行" else "操作失败: ${result.error}")
            }
        }
    }

    fun openUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.openUrl(url)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "已打开链接" else "打开失败: ${result.error}")
            }
        }
    }

    fun launchApp(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDialog = "") }
            val result = AdbService.launchApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "应用已启动" else "启动失败: ${result.error}")
            }
        }
    }

    private fun showCurrentActivity() {
        viewModelScope.launch {
            val result = AdbService.dumpActivity()
            _uiState.update {
                it.copy(statusMessage = if (result.success) result.output else "获取失败: ${result.error}")
            }
        }
    }

    private fun showLogcat() {
        viewModelScope.launch {
            val result = AdbService.getLogcat(200)
            _uiState.update {
                it.copy(
                    activeDialog = "logcat_view",
                    commandOutput = if (result.success) result.output else "获取失败: ${result.error}"
                )
            }
        }
    }

    fun clearLogcat() {
        viewModelScope.launch {
            AdbService.clearLogcat()
            _uiState.update { it.copy(commandOutput = "日志已清除") }
        }
    }

    private fun showSystemProps() {
        viewModelScope.launch {
            val result = AdbService.shell("getprop")
            _uiState.update {
                it.copy(
                    activeDialog = "logcat_view",
                    commandOutput = if (result.success) result.output else "获取失败: ${result.error}"
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
                it.copy(statusMessage = if (result.success) "屏幕密度已修改" else "修改失败: ${result.error}")
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
                it.copy(statusMessage = if (result.success) "分辨率已修改" else "修改失败: ${result.error}")
            }
        }
    }

    private fun toggleNavBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.navigation=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "导航栏已隐藏" else "操作失败: ${result.error}")
            }
        }
    }

    private fun toggleStatusBar() {
        viewModelScope.launch {
            val result = AdbService.shell("settings put global policy_control immersive.status=*")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "状态栏已隐藏" else "操作失败: ${result.error}")
            }
        }
    }
}
