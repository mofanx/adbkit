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

data class StorageInfo(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val availableBytes: Long = 0
) {
    val usedPercent: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class DeviceInfoUiState(
    val deviceInfo: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String = "",
    val storageInfo: StorageInfo = StorageInfo()
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
                val storage = loadStorageInfo()
                _uiState.update { it.copy(deviceInfo = info, isLoading = false, storageInfo = storage) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to get info", isLoading = false) }
            }
        }
    }

    private suspend fun loadStorageInfo(): StorageInfo {
        val result = AdbService.shell("df /data | tail -1")
        if (!result.success) return StorageInfo()
        // df output: Filesystem 1K-blocks Used Available Use% Mounted on
        val parts = result.output.trim().split("\\s+".toRegex())
        if (parts.size < 4) return StorageInfo()
        // Values are in 1K blocks
        val totalKb = parts[1].toLongOrNull() ?: 0
        val usedKb = parts[2].toLongOrNull() ?: 0
        val availKb = parts[3].toLongOrNull() ?: 0
        return StorageInfo(
            totalBytes = totalKb * 1024,
            usedBytes = usedKb * 1024,
            availableBytes = availKb * 1024
        )
    }

    fun copyAll() {
        val info = _uiState.value.deviceInfo
        if (info.isEmpty()) return
        val text = info.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val clipboard = AdbKitApplication.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Device Info", text))
    }
}
