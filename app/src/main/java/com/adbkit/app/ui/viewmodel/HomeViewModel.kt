package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val ipAddress: String = "",
    val connectedDevices: List<String> = emptyList(),
    val selectedDevice: String? = null,
    val isConnecting: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val showPairDialog: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshDevices()
    }

    fun updateIpAddress(ip: String) {
        _uiState.update { it.copy(ipAddress = ip) }
    }

    fun connectDevice() {
        val ip = _uiState.value.ipAddress.trim()
        if (ip.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "请输入IP地址", isError = true) }
            return
        }
        val address = if (ip.contains(":")) ip else "$ip:5555"
        _uiState.update { it.copy(isConnecting = true, statusMessage = "正在连接...") }
        viewModelScope.launch {
            val result = AdbService.connect(address)
            if (result.success && result.output.contains("connected")) {
                AdbService.setCurrentDevice(address)
                refreshDevices()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = "连接成功",
                        isError = false,
                        selectedDevice = address
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = result.error.ifEmpty { result.output.ifEmpty { "连接失败" } },
                        isError = true
                    )
                }
            }
        }
    }

    fun disconnectDevice(device: String) {
        viewModelScope.launch {
            AdbService.disconnect(device)
            if (_uiState.value.selectedDevice == device) {
                AdbService.setCurrentDevice(null)
            }
            refreshDevices()
            _uiState.update { it.copy(statusMessage = "已断开 $device", isError = false) }
        }
    }

    fun selectDevice(device: String) {
        AdbService.setCurrentDevice(device)
        _uiState.update { it.copy(selectedDevice = device, statusMessage = "已选择 $device") }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            val devices = AdbService.getConnectedDevices()
            _uiState.update { it.copy(connectedDevices = devices) }
        }
    }

    fun scanDevices() {
        _uiState.update { it.copy(statusMessage = "正在扫描网络设备...", isError = false) }
        viewModelScope.launch {
            // Get local IP range
            val ipResult = AdbService.executeCommand("ip route | grep src | head -1")
            val localIp = if (ipResult.success) {
                "src\\s+(\\S+)".toRegex().find(ipResult.output)?.groupValues?.getOrNull(1) ?: ""
            } else ""

            if (localIp.isNotEmpty()) {
                val subnet = localIp.substringBeforeLast(".")
                _uiState.update { it.copy(ipAddress = subnet, statusMessage = "子网: $subnet.0/24") }
            } else {
                _uiState.update { it.copy(statusMessage = "无法获取本地IP", isError = true) }
            }
        }
    }

    fun togglePairDialog() {
        _uiState.update { it.copy(showPairDialog = !it.showPairDialog) }
    }

    fun pairDevice(ip: String, port: String, code: String) {
        if (ip.isBlank() || port.isBlank() || code.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请填写完整信息", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(showPairDialog = false, statusMessage = "正在配对...") }
            val result = AdbService.executeCommand("adb pair $ip:$port $code")
            if (result.success && result.output.contains("Successfully")) {
                _uiState.update { it.copy(statusMessage = "配对成功", isError = false) }
                refreshDevices()
            } else {
                _uiState.update {
                    it.copy(statusMessage = result.error.ifEmpty { "配对失败" }, isError = true)
                }
            }
        }
    }
}
