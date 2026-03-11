package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

data class HomeUiState(
    val ipAddress: String = "",
    val connectedDevices: List<String> = emptyList(),
    val selectedDevice: String? = null,
    val isConnecting: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val showPairDialog: Boolean = false,
    val showMoreMenu: Boolean = false,
    val connectionHistory: List<String> = emptyList(),
    val showHistoryDropdown: Boolean = false,
    val isScanning: Boolean = false,
    val showScanDialog: Boolean = false,
    val scannedDevices: List<String> = emptyList()
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val repo = SettingsRepository(AdbKitApplication.instance)

    init {
        refreshDevices()
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repo.connectionHistory.collect { history ->
                _uiState.update { it.copy(connectionHistory = history) }
            }
        }
    }

    fun updateIpAddress(ip: String) {
        _uiState.update { it.copy(ipAddress = ip) }
    }

    fun toggleHistoryDropdown() {
        _uiState.update { it.copy(showHistoryDropdown = !it.showHistoryDropdown) }
    }

    fun dismissHistoryDropdown() {
        _uiState.update { it.copy(showHistoryDropdown = false) }
    }

    fun selectHistory(address: String) {
        _uiState.update { it.copy(ipAddress = address, showHistoryDropdown = false) }
    }

    fun removeHistory(address: String) {
        viewModelScope.launch { repo.removeConnectionHistory(address) }
    }

    fun connectDevice() {
        val ip = _uiState.value.ipAddress.trim()
        if (ip.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Please enter IP address", isError = true) }
            return
        }
        val address = if (ip.contains(":")) ip else "$ip:5555"
        _uiState.update { it.copy(isConnecting = true, statusMessage = "Connecting...") }
        viewModelScope.launch {
            val result = AdbService.connect(address)
            if (result.success && result.output.contains("connected")) {
                AdbService.setCurrentDevice(address)
                repo.addConnectionHistory(address)
                refreshDevices()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = "Connected",
                        isError = false,
                        selectedDevice = address
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        statusMessage = result.error.ifEmpty { result.output.ifEmpty { "Connection failed" } },
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
            _uiState.update { it.copy(statusMessage = "Disconnected $device", isError = false) }
        }
    }

    fun selectDevice(device: String) {
        AdbService.setCurrentDevice(device)
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            val devices = AdbService.getConnectedDevices()
            _uiState.update { it.copy(connectedDevices = devices) }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (!name.startsWith("wlan") && !name.startsWith("eth") && !name.startsWith("ap")) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun scanDevices() {
        _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList(), showScanDialog = true) }
        viewModelScope.launch {
            val localIp = getLocalIpAddress()

            if (localIp.isNullOrEmpty()) {
                _uiState.update { it.copy(isScanning = false, statusMessage = "Cannot get local IP, check WiFi connection", isError = true) }
                return@launch
            }

            val subnet = localIp.substringBeforeLast(".")
            val found = java.util.concurrent.CopyOnWriteArrayList<String>()

            withContext(Dispatchers.IO) {
                // Phase 1: Try adb mdns discovery first (fastest & most reliable)
                val mdnsResult = AdbService.executeCommand("${AdbService.getAdbPath()} mdns services 2>/dev/null || true")
                if (mdnsResult.success && mdnsResult.output.isNotBlank()) {
                    mdnsResult.output.lines().forEach { line ->
                        val match = "(\\d+\\.\\d+\\.\\d+\\.\\d+):(\\d+)".toRegex().find(line)
                        if (match != null) {
                            found.add("${match.groupValues[1]}:${match.groupValues[2]}")
                        }
                    }
                }

                // Phase 2: Scan all IPs on common ADB port 5555
                val port5555Hosts = (1..254).map { i ->
                    async {
                        val ip = "$subnet.$i"
                        if (ip == localIp) return@async null
                        try {
                            val sock = java.net.Socket()
                            sock.connect(java.net.InetSocketAddress(ip, 5555), 300)
                            sock.close()
                            "$ip:5555"
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
                port5555Hosts.forEach { if (it !in found) found.add(it) }

                // Phase 3: For live hosts (responded on 5555 or from mdns),
                // plus a quick ARP/ping sweep to find other hosts, then scan wireless debug ports
                val knownHosts = found.map { it.substringBefore(":") }.toMutableSet()

                // Quick ping sweep for additional hosts
                val pingHosts = (1..254).map { i ->
                    async {
                        val ip = "$subnet.$i"
                        if (ip == localIp || ip in knownHosts) return@async null
                        try {
                            if (java.net.InetAddress.getByName(ip).isReachable(200)) ip else null
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull()
                knownHosts.addAll(pingHosts)

                // For all known live hosts, scan wireless debug port range (37000-44000, step 100)
                val wifiDebugJobs = knownHosts.flatMap { ip ->
                    (37000..44000 step 100).map { port ->
                        async {
                            try {
                                val sock = java.net.Socket()
                                sock.connect(java.net.InetSocketAddress(ip, port), 200)
                                sock.close()
                                "$ip:$port"
                            } catch (_: Exception) { null }
                        }
                    }
                }
                wifiDebugJobs.awaitAll().filterNotNull().forEach { if (it !in found) found.add(it) }
            }

            _uiState.update {
                it.copy(
                    isScanning = false,
                    scannedDevices = found.distinct(),
                    statusMessage = if (found.isEmpty()) "No devices found on $subnet.0/24" else "Found ${found.size} device(s)",
                    isError = found.isEmpty()
                )
            }
        }
    }

    fun dismissScanDialog() {
        _uiState.update { it.copy(showScanDialog = false) }
    }

    fun connectScannedDevice(address: String) {
        // Keep full address (ip:port) so wireless debug ports are preserved
        _uiState.update { it.copy(ipAddress = address, showScanDialog = false) }
        connectDevice()
    }

    fun togglePairDialog() {
        _uiState.update { it.copy(showPairDialog = !it.showPairDialog) }
    }

    fun toggleMoreMenu() {
        _uiState.update { it.copy(showMoreMenu = !it.showMoreMenu) }
    }

    fun dismissMoreMenu() {
        _uiState.update { it.copy(showMoreMenu = false) }
    }

    fun pairDevice(ip: String, port: String, code: String) {
        if (ip.isBlank() || port.isBlank() || code.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please fill in all fields", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(showPairDialog = false, statusMessage = "Pairing...") }
            val result = AdbService.executeCommand("${AdbService.getAdbPath()} pair $ip:$port $code")
            if (result.success && result.output.contains("Successfully")) {
                _uiState.update { it.copy(statusMessage = "Pairing successful", isError = false) }
                refreshDevices()
            } else {
                _uiState.update {
                    it.copy(statusMessage = result.error.ifEmpty { "Pairing failed" }, isError = true)
                }
            }
        }
    }

    fun restartAdbServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Restarting ADB...", isError = false) }
            val result = AdbService.restartServer()
            if (result.success) {
                _uiState.update { it.copy(statusMessage = "ADB restarted", isError = false) }
                refreshDevices()
            } else {
                _uiState.update {
                    it.copy(statusMessage = "Restart failed: ${result.error}", isError = true)
                }
            }
        }
    }

    fun disconnectAll() {
        viewModelScope.launch {
            AdbService.disconnectAll()
            AdbService.setCurrentDevice(null)
            refreshDevices()
            _uiState.update { it.copy(statusMessage = "All disconnected", isError = false, selectedDevice = null) }
        }
    }
}
