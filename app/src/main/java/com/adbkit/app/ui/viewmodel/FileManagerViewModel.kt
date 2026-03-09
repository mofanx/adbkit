package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileManagerUiState(
    val currentPath: String = "/sdcard",
    val files: List<Map<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String = "",
    val showCreateDirDialog: Boolean = false,
    val statusMessage: String = ""
)

class FileManagerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    private fun loadFiles() {
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(error = "No device connected", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = "") }
        viewModelScope.launch {
            try {
                val files = AdbService.listFiles(_uiState.value.currentPath)
                // Sort: directories first, then by name
                val sorted = files.sortedWith(
                    compareByDescending<Map<String, String>> { it["isDirectory"] == "true" }
                        .thenBy { it["name"]?.lowercase() }
                )
                _uiState.update { it.copy(files = sorted, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Load failed", isLoading = false) }
            }
        }
    }

    fun navigateTo(path: String) {
        _uiState.update { it.copy(currentPath = path) }
        loadFiles()
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/") return
        val parent = current.substringBeforeLast("/").ifEmpty { "/" }
        navigateTo(parent)
    }

    fun navigateToHome() {
        navigateTo("/sdcard")
    }

    fun refresh() {
        loadFiles()
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            val result = AdbService.deleteFile(path)
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = "Delete failed: ${result.error}") }
            }
        }
    }

    fun pullFile(remotePath: String, fileName: String) {
        viewModelScope.launch {
            val localPath = "/sdcard/Download/$fileName"
            val result = AdbService.pullFile(remotePath, localPath)
            _uiState.update {
                it.copy(statusMessage = if (result.success) "Downloaded to $localPath" else "Download failed: ${result.error}")
            }
        }
    }

    fun showCreateDirDialog() {
        _uiState.update { it.copy(showCreateDirDialog = true) }
    }

    fun hideCreateDirDialog() {
        _uiState.update { it.copy(showCreateDirDialog = false) }
    }

    fun createDirectory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(showCreateDirDialog = false) }
            val path = "${_uiState.value.currentPath}/$name"
            val result = AdbService.createDirectory(path)
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = "Create failed: ${result.error}") }
            }
        }
    }
}
