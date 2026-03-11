package com.adbkit.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RemoteControlUiState(
    val resolution: String = "auto",
    val bitrate: String = "8Mbps",
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
    val screenBitmap: Bitmap? = null,
    val fps: Int = 0,
    val refreshInterval: Long = 200L,
    val captureScale: Float = 1.0f,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0
)

class RemoteControlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null
    private var frameCount = 0
    private var lastFpsTime = 0L

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

    fun setRefreshRate(rate: String) {
        val interval = when {
            rate.contains("2fps") -> 500L
            rate.contains("5fps") -> 200L
            rate.contains("10fps") -> 100L
            rate.contains("20fps") -> 50L
            else -> 200L
        }
        _uiState.update { it.copy(refreshInterval = interval) }
    }

    fun startRemoteControl() {
        if (_uiState.value.isConnected) {
            stopCapture()
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
                startCapture()
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

    private fun startCapture() {
        captureJob?.cancel()
        frameCount = 0
        lastFpsTime = System.currentTimeMillis()
        captureJob = viewModelScope.launch {
            while (isActive && _uiState.value.isConnected) {
                val bitmap = AdbService.captureScreenBitmap()
                if (bitmap != null) {
                    frameCount++
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastFpsTime
                    val fps = if (elapsed > 0) (frameCount * 1000 / elapsed).toInt() else 0
                    if (elapsed > 2000) {
                        frameCount = 0
                        lastFpsTime = now
                    }
                    _uiState.update {
                        it.copy(
                            screenBitmap = bitmap,
                            fps = fps,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    }
                }
                delay(_uiState.value.refreshInterval)
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    fun sendKey(keyCode: Int) {
        viewModelScope.launch {
            AdbService.inputKeyEvent(keyCode)
        }
    }

    /**
     * Convert view ratio coordinates to actual device screen coordinates.
     * Uses bitmap dimensions (actual screenshot size) which may differ from
     * the reported screen size (wm size), ensuring 1:1 mapping accuracy.
     */
    private fun ratioToDeviceCoords(xRatio: Float, yRatio: Float): Pair<Int, Int> {
        val state = _uiState.value
        // Use bitmap dimensions if available (most accurate), fallback to wm size
        val w = if (state.bitmapWidth > 0) state.bitmapWidth else state.screenWidth
        val h = if (state.bitmapHeight > 0) state.bitmapHeight else state.screenHeight
        val x = (xRatio.coerceIn(0f, 1f) * w).toInt()
        val y = (yRatio.coerceIn(0f, 1f) * h).toInt()
        return Pair(x, y)
    }

    fun sendTapAt(xRatio: Float, yRatio: Float) {
        val (x, y) = ratioToDeviceCoords(xRatio, yRatio)
        viewModelScope.launch {
            if (_uiState.value.compatMode) {
                // Compat mode: use shell input tap with sendevent fallback
                val result = AdbService.inputTap(x, y)
                if (!result.success) {
                    AdbService.shell("input touchscreen tap $x $y")
                }
            } else {
                AdbService.inputTap(x, y)
            }
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
        val w = if (state.bitmapWidth > 0) state.bitmapWidth else state.screenWidth
        val h = if (state.bitmapHeight > 0) state.bitmapHeight else state.screenHeight
        viewModelScope.launch {
            AdbService.inputTap(w / 2, h / 2)
        }
    }

    fun sendSwipe(direction: String) {
        val state = _uiState.value
        val w = if (state.bitmapWidth > 0) state.bitmapWidth else state.screenWidth
        val h = if (state.bitmapHeight > 0) state.bitmapHeight else state.screenHeight
        val cx = w / 2
        val cy = h / 2
        val dist = h / 4
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
        stopCapture()
    }
}
