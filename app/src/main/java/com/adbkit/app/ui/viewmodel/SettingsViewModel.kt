package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val adbPath: String = "adb",
    val fastbootPath: String = "fastboot",
    val defaultPort: String = "5555",
    val autoConnect: Boolean = true,
    val darkMode: Boolean = false,
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = false,
    val confirmDangerous: Boolean = true,
    val saveHistory: Boolean = true,
    val adbStatus: String = "",
    val isCheckingAdb: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val repo = SettingsRepository(AdbKitApplication.instance)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repo.adbPath.collect { v -> _uiState.update { it.copy(adbPath = v) } } }
        viewModelScope.launch { repo.fastbootPath.collect { v -> _uiState.update { it.copy(fastbootPath = v) } } }
        viewModelScope.launch { repo.defaultPort.collect { v -> _uiState.update { it.copy(defaultPort = v) } } }
        viewModelScope.launch { repo.autoConnect.collect { v -> _uiState.update { it.copy(autoConnect = v) } } }
        viewModelScope.launch { repo.darkMode.collect { v -> _uiState.update { it.copy(darkMode = v) } } }
        viewModelScope.launch { repo.dynamicColor.collect { v -> _uiState.update { it.copy(dynamicColor = v) } } }
        viewModelScope.launch { repo.keepScreenOn.collect { v -> _uiState.update { it.copy(keepScreenOn = v) } } }
        viewModelScope.launch { repo.confirmDangerous.collect { v -> _uiState.update { it.copy(confirmDangerous = v) } } }
        viewModelScope.launch { repo.saveHistory.collect { v -> _uiState.update { it.copy(saveHistory = v) } } }
    }

    fun setAdbPath(path: String) {
        _uiState.update { it.copy(adbPath = path) }
        viewModelScope.launch {
            repo.setAdbPath(path)
            AdbService.setAdbPath(path)
        }
    }

    fun setFastbootPath(path: String) {
        _uiState.update { it.copy(fastbootPath = path) }
        viewModelScope.launch { repo.setFastbootPath(path) }
    }

    fun setDefaultPort(port: String) {
        _uiState.update { it.copy(defaultPort = port) }
        viewModelScope.launch { repo.setDefaultPort(port) }
    }

    fun setAutoConnect(value: Boolean) {
        _uiState.update { it.copy(autoConnect = value) }
        viewModelScope.launch { repo.setAutoConnect(value) }
    }

    fun setDarkMode(value: Boolean) {
        _uiState.update { it.copy(darkMode = value) }
        viewModelScope.launch { repo.setDarkMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        _uiState.update { it.copy(dynamicColor = value) }
        viewModelScope.launch { repo.setDynamicColor(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        _uiState.update { it.copy(keepScreenOn = value) }
        viewModelScope.launch { repo.setKeepScreenOn(value) }
    }

    fun setConfirmDangerous(value: Boolean) {
        _uiState.update { it.copy(confirmDangerous = value) }
        viewModelScope.launch { repo.setConfirmDangerous(value) }
    }

    fun setSaveHistory(value: Boolean) {
        _uiState.update { it.copy(saveHistory = value) }
        viewModelScope.launch { repo.setSaveHistory(value) }
    }

    fun checkAdbAvailability() {
        _uiState.update { it.copy(isCheckingAdb = true, adbStatus = "检测中...") }
        viewModelScope.launch {
            val path = _uiState.value.adbPath
            val result = AdbService.executeCommand("$path version")
            val status = if (result.success) {
                "✓ ADB 可用: ${result.output.lines().firstOrNull() ?: ""}"
            } else {
                "✗ ADB 不可用: ${result.error.ifEmpty { "未找到adb" }}"
            }
            _uiState.update { it.copy(isCheckingAdb = false, adbStatus = status) }
        }
    }

    fun autoDetectAdb() {
        _uiState.update { it.copy(isCheckingAdb = true, adbStatus = "自动检测中...") }
        viewModelScope.launch {
            val candidatePaths = listOf(
                "adb",
                "/data/local/tmp/adb",
                "${AdbKitApplication.instance.filesDir.absolutePath}/adb",
                "/system/bin/adb",
                "/system/xbin/adb",
                "/sbin/adb",
                "/vendor/bin/adb"
            )
            var found = false
            for (candidate in candidatePaths) {
                val result = AdbService.executeCommand("$candidate version")
                if (result.success && result.output.contains("Android Debug Bridge")) {
                    setAdbPath(candidate)
                    _uiState.update {
                        it.copy(
                            isCheckingAdb = false,
                            adbStatus = "✓ 已找到: $candidate\n${result.output.lines().firstOrNull() ?: ""}"
                        )
                    }
                    found = true
                    break
                }
            }
            if (!found) {
                _uiState.update {
                    it.copy(
                        isCheckingAdb = false,
                        adbStatus = "✗ 未找到可用的adb\n\n请手动放置adb到以下路径之一:\n" +
                                "• ${AdbKitApplication.instance.filesDir.absolutePath}/adb\n" +
                                "• /data/local/tmp/adb\n\n" +
                                "或在上方手动指定adb路径"
                    )
                }
            }
        }
    }
}
