package com.adbkit.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val appDetails: Map<String, String> = emptyMap(),
    val appPermissions: List<String> = emptyList(),
    val appIcon: Bitmap? = null,
    val appComponents: Map<String, String> = emptyMap(),
    val isInstalling: Boolean = false,
    val requestApkPick: Boolean = false
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

class AppManagerViewModel : LocalizedViewModel() {
    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(error = strings.noDeviceConnected, isLoading = false) }
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
                _uiState.update { it.copy(error = e.message ?: strings.loadFailed, isLoading = false) }
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
                it.copy(statusMessage = if (result.success) strings.appForceStopped(pkg) else strings.forceStopFailed(result.error))
            }
        }
    }

    fun uninstall(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.uninstallApp(pkg)
            if (result.success) {
                refresh()
                _uiState.update { it.copy(statusMessage = strings.appUninstalled(pkg)) }
            } else {
                _uiState.update { it.copy(statusMessage = strings.uninstallFailed(result.error)) }
            }
        }
    }

    fun clearData(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.clearAppData(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.appDataCleared(pkg) else strings.clearDataFailed(result.error))
            }
        }
    }

    fun disable(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.disableApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.appDisabled(pkg) else strings.disableFailed(result.error))
            }
        }
    }

    fun enable(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.enableApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.appEnabled(pkg) else strings.enableFailed(result.error))
            }
        }
    }

    fun backup(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = strings.backingUpApp(pkg)) }
            val root = AdbService.getDeviceExternalStorageRoot()
            val savePath = "$root/Download/${pkg}.apk"
            val result = AdbService.backupApp(pkg, savePath)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.backedUpTo(savePath) else strings.backupFailed(result.error))
            }
        }
    }

    fun launch(pkg: String) {
        viewModelScope.launch {
            val result = AdbService.launchApp(pkg)
            _uiState.update {
                it.copy(statusMessage = if (result.success) strings.appLaunched(pkg) else strings.launchFailed(result.error))
            }
        }
    }

    fun showDetail(pkg: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedPackage = pkg, showDetailDialog = true) }
            val details = AdbService.getAppDetail(pkg)
            val permissions = AdbService.getAppPermissions(pkg)
            val icon = AdbService.getAppIcon(pkg)
            val components = AdbService.getAppComponentCounts(pkg)
            _uiState.update { it.copy(appDetails = details, appPermissions = permissions, appIcon = icon, appComponents = components) }
        }
    }

    fun hideDetail() {
        _uiState.update {
            it.copy(
                showDetailDialog = false,
                selectedPackage = "",
                appDetails = emptyMap(),
                appPermissions = emptyList(),
                appIcon = null,
                appComponents = emptyMap()
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = "") }
    }

    fun requestInstallApk() {
        _uiState.update { it.copy(requestApkPick = true) }
    }

    fun onApkPickHandled() {
        _uiState.update { it.copy(requestApkPick = false) }
    }

    fun installApk(localPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInstalling = true, statusMessage = strings.installing) }
            // Push APK to device temp, then install
            val remotePath = "/data/local/tmp/install_temp.apk"
            val pushResult = AdbService.pushFile(localPath, remotePath)
            if (!pushResult.success) {
                File(localPath).delete()
                _uiState.update { it.copy(isInstalling = false, statusMessage = strings.pushFailed(pushResult.error)) }
                return@launch
            }
            val installResult = AdbService.installApp(remotePath)
            // Clean up temp files
            AdbService.shell("rm -f $remotePath")
            File(localPath).delete()
            _uiState.update {
                it.copy(
                    isInstalling = false,
                    statusMessage = if (installResult.success) strings.installSuccess else strings.installFailedFmt(installResult.error)
                )
            }
            if (installResult.success) refresh()
        }
    }

    fun batchBackupFiltered() {
        viewModelScope.launch {
            val packages = _uiState.value.filteredPackages
            if (packages.isEmpty()) {
                _uiState.update { it.copy(statusMessage = strings.noAppsToBackup) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, statusMessage = strings.backingUpApps(packages.size)) }
            var successCount = 0
            var failedCount = 0
            val destDir = "${AdbService.getDeviceExternalStorageRoot()}/app_backup_adbkit"
            packages.forEach { pkg ->
                val result = AdbService.backupApk(pkg, destDir)
                if (result.success) successCount++ else failedCount++
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = strings.backupComplete(successCount, failedCount, destDir)
                )
            }
        }
    }

    fun exportAppList(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = strings.exportingAppList) }
            try {
                val packages = if (_uiState.value.showSystemApps) _uiState.value.systemPackages else _uiState.value.userPackages
                val typeLabel = if (_uiState.value.showSystemApps) "system" else "user"
                val content = buildString {
                    appendLine("# ADB Kit App List (${typeLabel}, ${packages.size} packages)")
                    appendLine("# Generated at ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date())}")
                    packages.forEach { appendLine(it) }
                }
                val tempFile = File(context.cacheDir, "app_list_adbkit.txt")
                tempFile.writeText(content)
                val root = AdbService.getDeviceExternalStorageRoot()
                val remotePath = "$root/app_list_adbkit.txt"
                val result = AdbService.pushFile(tempFile.absolutePath, remotePath)
                tempFile.delete()
                _uiState.update {
                    it.copy(statusMessage = if (result.success) strings.appListExported(remotePath) else strings.exportFailed(result.error))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = strings.exportFailed(e.message ?: "")) }
            }
        }
    }
}
