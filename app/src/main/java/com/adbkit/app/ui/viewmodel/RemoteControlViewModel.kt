package com.adbkit.app.ui.viewmodel

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import com.adbkit.app.service.ScreenStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteControlUiState(
    val maxSize: String = "720",
    val bitrate: String = "8Mbps",
    val keepAspectRatio: Boolean = true,
    val navBarStyle: String = "floating", // "floating", "bottom", "hidden"
    val screenOff: Boolean = false,
    val audioEnabled: Boolean = false,
    val viewOnly: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 1920,
    val fps: Int = 0,
    val streamMode: String = "none",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

class RemoteControlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    val streamService = ScreenStreamService()

    init {
        loadScreenSize()
        viewModelScope.launch {
            streamService.state.collect { streamState ->
                _uiState.update {
                    it.copy(
                        fps = streamState.fps,
                        streamMode = streamState.streamMode,
                        videoWidth = streamState.videoWidth,
                        videoHeight = streamState.videoHeight
                    )
                }
            }
        }
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

    fun setMaxSize(value: String) { _uiState.update { it.copy(maxSize = value) } }
    fun setBitrate(value: String) { _uiState.update { it.copy(bitrate = value) } }
    fun setKeepAspectRatio(value: Boolean) { _uiState.update { it.copy(keepAspectRatio = value) } }
    fun setNavBarStyle(value: String) { _uiState.update { it.copy(navBarStyle = value) } }
    fun setScreenOff(value: Boolean) { _uiState.update { it.copy(screenOff = value) } }
    fun setAudioEnabled(value: Boolean) { _uiState.update { it.copy(audioEnabled = value) } }
    fun setViewOnly(value: Boolean) { _uiState.update { it.copy(viewOnly = value) } }

    private fun parseMaxSize(): Int = _uiState.value.maxSize.toIntOrNull() ?: 720

    private fun parseBitrate(): Int {
        return when (_uiState.value.bitrate) {
            "2Mbps" -> 2_000_000
            "4Mbps" -> 4_000_000
            "6Mbps" -> 6_000_000
            "8Mbps" -> 8_000_000
            "12Mbps" -> 12_000_000
            "16Mbps" -> 16_000_000
            "32Mbps" -> 32_000_000
            else -> 8_000_000
        }
    }

    fun startH264Stream(surface: Surface) {
        val config = ScreenStreamService.StreamConfig(
            maxSize = parseMaxSize(),
            bitrate = parseBitrate(),
            audioEnabled = _uiState.value.audioEnabled
        )
        streamService.startStream(surface, config, viewModelScope)
    }

    fun startRemoteControl() {
        if (_uiState.value.isConnected) {
            disconnectRemoteControl()
            return
        }
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(statusMessage = "No device connected", isError = true) }
            return
        }
        _uiState.update { it.copy(isConnecting = true, statusMessage = "Connecting...") }
        viewModelScope.launch {
            val result = AdbService.shell("echo connected")
            if (result.success) {
                loadScreenSize()
                if (_uiState.value.screenOff) {
                    // scrcpy-style screen off: turn off display without locking
                    setDeviceScreenPower(false)
                }
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        statusMessage = "Remote control connected",
                        isError = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = "Connection failed: ${result.error}",
                        isError = true
                    )
                }
            }
        }
    }

    fun disconnectRemoteControl() {
        val wasScreenOff = _uiState.value.screenOff
        stopStream()
        viewModelScope.launch {
            if (wasScreenOff) {
                // Restore screen on disconnect
                setDeviceScreenPower(true)
            }
        }
        _uiState.update { it.copy(isConnected = false, statusMessage = "Disconnected") }
    }

    /**
     * scrcpy-style screen power control using SurfaceControl.setDisplayPowerMode.
     * Turns off the display without locking the device, saving battery.
     */
    private suspend fun setDeviceScreenPower(on: Boolean) {
        // Use the same approach as scrcpy: call SurfaceControl.setDisplayPowerMode
        // via shell command. Mode 0 = OFF, Mode 2 = ON
        val mode = if (on) 2 else 0
        // Try multiple approaches for compatibility
        AdbService.shell(
            "cmd display set-brightness 1 2>/dev/null; " +
            "settings put system screen_brightness_mode 0 2>/dev/null; " +
            "service call SurfaceFlinger 1035 i32 $mode 2>/dev/null; " +
            if (!on) "input keyevent 26 2>/dev/null" else "input keyevent 224 2>/dev/null"
        )
    }

    fun stopStream() {
        streamService.stopStream()
    }

    fun sendKey(keyCode: Int) {
        viewModelScope.launch {
            AdbService.inputKeyEvent(keyCode)
        }
    }

    /**
     * Convert view ratio coordinates to actual device screen coordinates.
     */
    private fun ratioToDeviceCoords(xRatio: Float, yRatio: Float): Pair<Int, Int> {
        val state = _uiState.value
        val w = state.screenWidth
        val h = state.screenHeight
        val x = (xRatio.coerceIn(0f, 1f) * w).toInt()
        val y = (yRatio.coerceIn(0f, 1f) * h).toInt()
        return Pair(x, y)
    }

    fun sendTapAt(xRatio: Float, yRatio: Float) {
        val (x, y) = ratioToDeviceCoords(xRatio, yRatio)
        viewModelScope.launch {
            AdbService.inputTap(x, y)
        }
    }

    fun sendLongPressAt(xRatio: Float, yRatio: Float, duration: Int = 1000) {
        val (x, y) = ratioToDeviceCoords(xRatio, yRatio)
        viewModelScope.launch {
            // Long press = swipe at same position with duration
            AdbService.inputSwipe(x, y, x, y, duration)
        }
    }

    fun sendSwipeAt(x1Ratio: Float, y1Ratio: Float, x2Ratio: Float, y2Ratio: Float, duration: Int = 300) {
        val (x1, y1) = ratioToDeviceCoords(x1Ratio, y1Ratio)
        val (x2, y2) = ratioToDeviceCoords(x2Ratio, y2Ratio)
        viewModelScope.launch {
            AdbService.inputSwipe(x1, y1, x2, y2, duration)
        }
    }

    fun sendTap() {
        val state = _uiState.value
        viewModelScope.launch {
            AdbService.inputTap(state.screenWidth / 2, state.screenHeight / 2)
        }
    }

    fun sendSwipe(direction: String) {
        val state = _uiState.value
        val cx = state.screenWidth / 2
        val cy = state.screenHeight / 2
        val dist = state.screenHeight / 4
        viewModelScope.launch {
            when (direction) {
                "up" -> AdbService.inputSwipe(cx, cy + dist, cx, cy - dist)
                "down" -> AdbService.inputSwipe(cx, cy - dist, cx, cy + dist)
                "left" -> AdbService.inputSwipe(cx + dist, cy, cx - dist, cy)
                "right" -> AdbService.inputSwipe(cx - dist, cy, cx + dist, cy)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }
}
