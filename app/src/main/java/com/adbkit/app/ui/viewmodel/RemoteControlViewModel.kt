package com.adbkit.app.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.service.AdbService
import com.adbkit.app.service.ScreenStreamService
import com.adbkit.app.ui.strings.AppStrings
import com.adbkit.app.ui.strings.EnStrings
import com.adbkit.app.ui.strings.ZhStrings
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class RemoteControlUiState(
    val maxSize: String = "720",
    val bitrate: String = "8Mbps",
    val keepAspectRatio: Boolean = true,
    val navBarStyle: String = "floating", // "floating", "bottom", "hidden"
    val screenOff: Boolean = false,
    val audioEnabled: Boolean = false,
    val viewOnly: Boolean = false,
    val weakNetworkMode: Boolean = false,
    val fpsLowWarning: Boolean = false,
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

    private var lowFpsFrames = 0
    private var wasStreaming = false
    private var strings: AppStrings = resolveLanguage("system")

    private fun resolveLanguage(language: String): AppStrings = when (language) {
        "zh" -> ZhStrings
        "en" -> EnStrings
        else -> if (Locale.getDefault().language == "zh") ZhStrings else EnStrings
    }

    private fun resolveStreamError(error: String): String = when {
        error.contains("REMOTE_CONTROL_SERVER_MISSING") -> strings.remoteControlServerMissing
        error.contains("REMOTE_CONTROL_PUSH_FAILED|") -> strings.remoteControlPushFailed + error.substringAfter("|")
        error.contains("REMOTE_CONTROL_PUSH_FAILED") -> strings.remoteControlPushFailed
        else -> error
    }

    init {
        loadScreenSize()
        viewModelScope.launch {
            SettingsRepository(AdbKitApplication.instance).language.collect { lang ->
                strings = resolveLanguage(lang)
            }
        }
        viewModelScope.launch {
            streamService.state.collect { streamState ->
                val fps = streamState.fps
                val streaming = streamState.streamMode == "h264"
                val low = streaming && fps in 1..15
                if (low) lowFpsFrames++ else lowFpsFrames = 0
                _uiState.update {
                    it.copy(
                        fps = fps,
                        streamMode = streamState.streamMode,
                        videoWidth = streamState.videoWidth,
                        videoHeight = streamState.videoHeight,
                        fpsLowWarning = lowFpsFrames >= 5
                    )
                }
                // Detect unexpected stream stop and return to settings
                if (!streaming && wasStreaming) {
                    _uiState.update { current ->
                        if (current.isConnected) {
                            current.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusMessage = if (streamState.error.isNotBlank()) strings.remoteControlStreamError + resolveStreamError(streamState.error) else current.statusMessage,
                                isError = streamState.error.isNotBlank()
                            )
                        } else current
                    }
                }
                if (streaming) wasStreaming = true
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
    fun setScreenOff(value: Boolean) {
        _uiState.update { it.copy(screenOff = value) }
        if (_uiState.value.isConnected) {
            viewModelScope.launch {
                val message = setDeviceScreenPower(!value)
                _uiState.update { it.copy(statusMessage = message, isError = message.contains("failed")) }
            }
        }
    }
    fun setAudioEnabled(value: Boolean) { _uiState.update { it.copy(audioEnabled = value) } }
    fun setViewOnly(value: Boolean) { _uiState.update { it.copy(viewOnly = value) } }

    fun setWeakNetworkMode(enabled: Boolean) { _uiState.update { it.copy(weakNetworkMode = enabled) } }

    fun applyWeakNetworkPreset() {
        _uiState.update {
            it.copy(
                maxSize = "480",
                bitrate = "2Mbps",
                weakNetworkMode = true,
                statusMessage = strings.weakNetworkApplied,
                isError = false
            )
        }
    }

    fun dismissFpsWarning() {
        lowFpsFrames = 0
        _uiState.update { it.copy(fpsLowWarning = false) }
    }

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
        wasStreaming = false
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(statusMessage = strings.remoteControlNoDevice, isError = true) }
            return
        }
        _uiState.update { it.copy(isConnecting = true, isConnected = true, statusMessage = strings.remoteControlConnecting, isError = false) }
        viewModelScope.launch {
            val result = AdbService.shell("echo connected")
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        statusMessage = strings.remoteControlConnectionFailed + result.error,
                        isError = true
                    )
                }
                return@launch
            }
            loadScreenSize()
            val screenPowerMsg = if (_uiState.value.screenOff) setDeviceScreenPower(false) else ""
            try {
                val finalState = withTimeout(20_000) {
                    streamService.state.first { it.isStreaming || it.error.isNotEmpty() }
                }
                if (finalState.isStreaming) {
                    val base = strings.remoteControlConnected
                    val msg = if (screenPowerMsg.isBlank() || !screenPowerMsg.contains("failed")) base else "$base (${screenPowerMsg})"
                    _uiState.update { it.copy(isConnecting = false, statusMessage = msg, isError = false) }
                } else {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            statusMessage = strings.remoteControlStreamError + resolveStreamError(finalState.error),
                            isError = true
                        )
                    }
                    stopStream()
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        statusMessage = strings.remoteControlConnectionFailed + e.message,
                        isError = true
                    )
                }
                stopStream()
            }
        }
    }

    fun disconnectRemoteControl() {
        val wasScreenOff = _uiState.value.screenOff
        wasStreaming = false
        stopStream()
        _uiState.update { it.copy(isConnected = false, isConnecting = false) }
        viewModelScope.launch {
            val screenPowerMsg = if (wasScreenOff) setDeviceScreenPower(true) else ""
            val base = strings.disconnected
            _uiState.update {
                it.copy(
                    statusMessage = if (screenPowerMsg.isBlank()) base else "$base (${screenPowerMsg})",
                    isError = screenPowerMsg.contains("failed")
                )
            }
        }
    }

    /**
     * scrcpy-style screen power control using SurfaceControl.setDisplayPowerMode.
     * Turns off the display without locking the device, saving battery.
     * Returns a status message to show in the UI.
     */
    private suspend fun setDeviceScreenPower(on: Boolean): String {
        val mode = if (on) 2 else 0
        val root = AdbService.hasRootAccess()
        val commands = listOfNotNull(
            if (root) "su -c 'service call SurfaceFlinger 1035 i32 $mode' 2>/dev/null" else null,
            "service call SurfaceFlinger 1035 i32 $mode 2>/dev/null",
            "cmd display set-brightness ${if (on) "255" else "1"} 2>/dev/null",
            "settings put system screen_brightness ${if (on) "200" else "1"} 2>/dev/null",
            if (!on) "svc power goToSleep 2>/dev/null" else null,
            if (!on) "input keyevent 26 2>/dev/null" else null,
            if (on) "input keyevent 224 2>/dev/null" else null
        )

        commands.forEach { cmd ->
            try {
                val result = AdbService.shell(cmd)
                if (result.success) {
                    return if (on) strings.screenOnCommandSent else strings.screenOffCommandSent
                }
            } catch (_: Exception) {}
        }
        return if (on) strings.screenOnFailed else strings.screenOffFailed
    }

    fun stopStream() {
        streamService.stopStream()
    }

    fun sendKey(keyCode: Int) {
        viewModelScope.launch {
            AdbService.inputKeyEvent(keyCode)
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = AdbService.inputText(text)
            _uiState.update {
                it.copy(
                    statusMessage = if (result.success) strings.remoteControlTextSent else strings.remoteControlTextSendFailed + result.error,
                    isError = !result.success
                )
            }
        }
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (text.isNotBlank()) {
            sendText(text)
        } else {
            _uiState.update { it.copy(statusMessage = strings.remoteControlClipboardEmpty, isError = true) }
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
