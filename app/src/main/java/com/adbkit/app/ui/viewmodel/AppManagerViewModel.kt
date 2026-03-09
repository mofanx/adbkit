package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppManagerUiState(
    val userPackages: List<String> = emptyList(),
    val systemPackages: List<String> = emptyList(),
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
    val error: String = "",
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val statusMessage: String = "",
    val showDetailDialog: Boolean = false,
    val selectedPackage: String = "",
    val appDetails: Map<String, String> = emptyMap()
) {
    val userAppCount: Int get() = userPackages.size
    val systemAppCount: Int get() = systemPackages.size
    val filteredPackages: List<String>
        get() {
            val packages = if (showSystemApps) systemPackages else userPackages
            return if (searchQuery.isEmpty()) packages
            else packages.filter { it.contains(searchQuery, ignoreCase = true) }
        }
}

class AppManagerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

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
                val userApps = AdbService.getInstalledPackages(systemApps = false)
                val sysApps = AdbService.getInstalledPackages(systemApps = true)
                _uiState.update {
                    it.copy(userPackages = userApps, systemPackages = sysApps, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Load failed", isLoading = false) }
            }
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _uiState.update { it.copy(showSystemApps = show) }
    }

    fun toggleSearch() {
        _uiState.update { it.copy(showSearch = !it.showSearch, searchQuery = "") }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun forceStop(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.forceStopApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "$pkg force stopped" else "Failed: ${result.error}")
            }
        }
    }

    fun uninstall(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.uninstallApp(pkg)
            if (result.success) {
                refresh()
                _uiState.update { it.copy(statusMessage = "$pkg uninstalled") }
            } else {
                _uiState.update { it.copy(statusMessage = "Uninstall failed: ${result.error}") }
            }
        }
    }

    fun clearData(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.clearAppData(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "$pkg data cleared" else "Clear failed: ${result.error}")
            }
        }
    }

    fun disable(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.disableApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "$pkg disabled" else "Disable failed: ${result.error}")
            }
        }
    }

    fun enable(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.enableApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "$pkg enabled" else "Enable failed: ${result.error}")
            }
        }
    }

    fun backup(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Backing up $pkg...") }
            val result = AdbService.backupApp(pkg, "/sdcard/Download/${pkg}.apk")
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Backed up to /sdcard/Download/${pkg}.apk" else "Backup failed: ${result.error}")
            }
        }
    }

    fun launch(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.launchApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "$pkg launched" else "Launch failed: ${result.error}")
            }
        }
    }

    fun showDetail(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedPackage = pkg, showDetailDialog = true) }
            val details = AdbService.getAppDetail(pkg)
            _uiState.update { it.copy(appDetails = details) }
        }
    }

    fun hideDetail() {
        _uiState.update { it.copy(showDetailDialog = false, selectedPackage = "", appDetails = emptyMap()) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = "") }
    }
}
