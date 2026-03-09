package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteControlUiState(
    val resolution: String = "自动调整",
    val bitrate: String = "8Mbps",
    val aspectRatio: String = "保持原始比例",
    val navBarPosition: String = "悬浮",
    val fullscreen: Boolean = false,
    val screenOff: Boolean = false,
    val compatMode: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 1920
)

class RemoteControlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    init {
        loadScreenSize()
    }

    private fun loadScreenSize() {
        viewModelScope.launch {
            val result = AdbService.shell("wm size")
            if (result.success) {
                val size = result.output.replace("Physical size: ", "").trim()
                val parts = size.split("x")
                if (parts.size == 2) {
                    _uiState.update {
                        it.copy(
                            screenWidth = parts[0].toIntOrNull() ?: 1080,
                            screenHeight = parts[1].toIntOrNull() ?: 1920
                        )
                    }
                }
            }
        }
    }

    fun setResolution(value: String) { _uiState.update { it.copy(resolution = value) } }
    fun setBitrate(value: String) { _uiState.update { it.copy(bitrate = value) } }
    fun setAspectRatio(value: String) { _uiState.update { it.copy(aspectRatio = value) } }
    fun setNavBarPosition(value: String) { _uiState.update { it.copy(navBarPosition = value) } }
    fun setFullscreen(value: Boolean) { _uiState.update { it.copy(fullscreen = value) } }
    fun setScreenOff(value: Boolean) { _uiState.update { it.copy(screenOff = value) } }
    fun setCompatMode(value: Boolean) { _uiState.update { it.copy(compatMode = value) } }

    fun startRemoteControl() {
        if (_uiState.value.isConnected) {
            _uiState.update { it.copy(isConnected = false, statusMessage = "已断开远程控制") }
            return
        }
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(statusMessage = "请先连接设备", isError = true) }
            return
        }
        _uiState.update { it.copy(isConnecting = true, statusMessage = "正在连接...") }
        viewModelScope.launch {
            // Test connection with a simple command
            val result = AdbService.shell("echo connected")
            if (result.success) {
                if (_uiState.value.screenOff) {
                    AdbService.shell("input keyevent 26") // Power off screen
                }
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        statusMessage = "远程控制已连接",
                        isError = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = "连接失败: ${result.error}",
                        isError = true
                    )
                }
            }
        }
    }

    fun sendKey(keyCode: Int) {
        viewModelScope.launch {
            AdbService.inputKeyEvent(keyCode)
        }
    }

    fun sendTap() {
        val w = _uiState.value.screenWidth
        val h = _uiState.value.screenHeight
        viewModelScope.launch {
            AdbService.inputTap(w / 2, h / 2)
        }
    }

    fun sendSwipe(direction: String) {
        val w = _uiState.value.screenWidth
        val h = _uiState.value.screenHeight
        val cx = w / 2
        val cy = h / 2
        val dist = 300

        viewModelScope.launch {
            when (direction) {
                "up" -> AdbService.inputSwipe(cx, cy + dist, cx, cy - dist)
                "down" -> AdbService.inputSwipe(cx, cy - dist, cx, cy + dist)
                "left" -> AdbService.inputSwipe(cx + dist, cy, cx - dist, cy)
                "right" -> AdbService.inputSwipe(cx - dist, cy, cx + dist, cy)
            }
        }
    }
}
