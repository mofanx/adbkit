package com.adbkit.app.ui.viewmodel

import android.graphics.Bitmap
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
    val resolution: String = "720p",
    val bitrate: String = "8Mbps",
    val maxFps: String = "30",
    val aspectRatio: String = "original",
    val navBarPosition: String = "floating",
    val fullscreen: Boolean = false,
    val screenOff: Boolean = false,
    val compatMode: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 1920,
    val fps: Int = 0,
    val streamMode: String = "none",
    // Fallback bitmap for screencap mode
    val screenBitmap: Bitmap? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0
)

class RemoteControlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    val streamService = ScreenStreamService()

    init {
        loadScreenSize()
        // Collect stream service state
        viewModelScope.launch {
            streamService.state.collect { streamState ->
                _uiState.update {
                    it.copy(
                        fps = streamState.fps,
                        streamMode = streamState.streamMode
                    )
                }
            }
        }
        // Collect fallback bitmap
        viewModelScope.launch {
            streamService.screenshotBitmap.collect { bitmap ->
                if (bitmap != null) {
                    _uiState.update {
                        it.copy(
                            screenBitmap = bitmap,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    }
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

    fun setResolution(value: String) { _uiState.update { it.copy(resolution = value) } }
    fun setBitrate(value: String) { _uiState.update { it.copy(bitrate = value) } }
    fun setMaxFps(value: String) { _uiState.update { it.copy(maxFps = value) } }
    fun setAspectRatio(value: String) { _uiState.update { it.copy(aspectRatio = value) } }
    fun setNavBarPosition(value: String) { _uiState.update { it.copy(navBarPosition = value) } }
    fun setFullscreen(value: Boolean) { _uiState.update { it.copy(fullscreen = value) } }
    fun setScreenOff(value: Boolean) { _uiState.update { it.copy(screenOff = value) } }
    fun setCompatMode(value: Boolean) { _uiState.update { it.copy(compatMode = value) } }

    private fun parseResolution(): Pair<Int, Int> {
        val state = _uiState.value
        return when (state.resolution) {
            "480p" -> Pair(480, (480 * state.screenHeight / state.screenWidth.coerceAtLeast(1)))
            "720p" -> Pair(720, (720 * state.screenHeight / state.screenWidth.coerceAtLeast(1)))
            "1080p" -> Pair(1080, (1080 * state.screenHeight / state.screenWidth.coerceAtLeast(1)))
            "original" -> Pair(state.screenWidth, state.screenHeight)
            else -> Pair(720, (720 * state.screenHeight / state.screenWidth.coerceAtLeast(1)))
        }
    }

    private fun parseBitrate(): Int {
        return when (_uiState.value.bitrate) {
            "2Mbps" -> 2_000_000
            "4Mbps" -> 4_000_000
            "8Mbps" -> 8_000_000
            "16Mbps" -> 16_000_000
            "32Mbps" -> 32_000_000
            else -> 8_000_000
        }
    }

    /**
     * Start H.264 stream to a Surface (SurfaceView/TextureView).
     * Called from the UI when the Surface is available.
     */
    fun startH264Stream(surface: Surface) {
        val (w, h) = parseResolution()
        val config = ScreenStreamService.StreamConfig(
            width = w,
            height = h,
            bitrate = parseBitrate(),
            maxFps = _uiState.value.maxFps.toIntOrNull() ?: 30,
            useH264 = true
        )
        streamService.startStream(surface, config, viewModelScope)
    }

    fun startRemoteControl() {
        if (_uiState.value.isConnected) {
            stopStream()
            _uiState.update { it.copy(isConnected = false, statusMessage = "Disconnected", screenBitmap = null) }
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
                    AdbService.shell("input keyevent 26")
                }
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        statusMessage = "Remote control connected",
                        isError = false
                    )
                }
                // Stream will be started when Surface becomes available in the UI
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
            if (_uiState.value.compatMode) {
                val result = AdbService.inputTap(x, y)
                if (!result.success) {
                    AdbService.shell("input touchscreen tap $x $y")
                }
            } else {
                AdbService.inputTap(x, y)
            }
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
            if (_uiState.value.compatMode) {
                val result = AdbService.inputSwipe(x1, y1, x2, y2, duration)
                if (!result.success) {
                    AdbService.shell("input touchscreen swipe $x1 $y1 $x2 $y2 $duration")
                }
            } else {
                AdbService.inputSwipe(x1, y1, x2, y2, duration)
            }
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
