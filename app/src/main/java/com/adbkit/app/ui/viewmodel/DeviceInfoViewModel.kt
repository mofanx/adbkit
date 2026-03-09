package com.adbkit.app.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceInfoUiState(
    val deviceInfo: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String = ""
)

class DeviceInfoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(error = "No device connected", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = "") }
        viewModelScope.launch {
            try {
                val info = AdbService.getDeviceInfo()
                _uiState.update { it.copy(deviceInfo = info, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to get info", isLoading = false) }
            }
        }
    }

    fun copyAll() {
        val info = _uiState.value.deviceInfo
        if (info.isEmpty()) return
        val text = info.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val clipboard = AdbKitApplication.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Device Info", text))
    }
}
