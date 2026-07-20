package com.adbkit.app.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class FilePreviewType { TEXT, IMAGE, UNSUPPORTED }

data class FilePreview(
    val path: String,
    val name: String,
    val type: FilePreviewType,
    val text: String = "",
    val bitmap: Bitmap? = null,
    val error: String = ""
)

data class FileManagerUiState(
    val currentPath: String = AdbService.DEFAULT_REMOTE_STORAGE,
    val files: List<Map<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String = "",
    val showCreateDirDialog: Boolean = false,
    val statusMessage: String = "",
    val isTransferring: Boolean = false,
    val transferBytes: Long = 0L,
    val transferTotal: Long = 0L,
    val requestFilePick: Boolean = false,
    val hasRootAccess: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val preview: FilePreview? = null
)

class FileManagerViewModel : LocalizedViewModel() {
    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val root = AdbService.getDeviceExternalStorageRoot()
            if (_uiState.value.currentPath == AdbService.DEFAULT_REMOTE_STORAGE) {
                _uiState.update { it.copy(currentPath = root) }
            }
            loadFiles()
        }
    }

    private fun loadFiles() {
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(error = strings.noDeviceConnected, isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = "") }
        viewModelScope.launch {
            try {
                val root = AdbService.hasRootAccess()
                val files = AdbService.listFiles(_uiState.value.currentPath)
                // Sort: directories first, then by name
                val sorted = files.sortedWith(
                    compareByDescending<Map<String, String>> { it["isDirectory"] == "true" }
                        .thenBy { it["name"]?.lowercase() }
                )
                _uiState.update { it.copy(files = sorted, isLoading = false, hasRootAccess = root, selectedFiles = emptySet(), isSelectionMode = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: strings.loadFailed, isLoading = false) }
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
        viewModelScope.launch {
            val root = AdbService.getDeviceExternalStorageRoot()
            navigateTo(root)
        }
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
                _uiState.update { it.copy(error = strings.deleteFailed(result.error)) }
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = AdbService.renameFile(path, newName)
            _uiState.update { it.copy(isLoading = false) }
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = strings.renameFailed(result.error)) }
            }
        }
    }

    fun moveFile(path: String, destPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = AdbService.moveFile(path, destPath)
            _uiState.update { it.copy(isLoading = false) }
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = strings.moveFailed(result.error)) }
            }
        }
    }

    fun copyFile(path: String, destPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = AdbService.copyFile(path, destPath)
            _uiState.update { it.copy(isLoading = false) }
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = strings.copyFailed(result.error)) }
            }
        }
    }

    fun toggleFileSelection(path: String) {
        _uiState.update { state ->
            val current = state.selectedFiles
            val newSet = if (current.contains(path)) current - path else current + path
            state.copy(selectedFiles = newSet, isSelectionMode = true)
        }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedFiles = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { state ->
            val all = state.files.mapNotNull { it["path"] }.toSet()
            state.copy(selectedFiles = all, isSelectionMode = true)
        }
    }

    fun batchDelete() {
        val paths = _uiState.value.selectedFiles.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val results = paths.map { AdbService.deleteFile(it) }
            val failed = results.filterNot { it.success }
            _uiState.update { it.copy(isLoading = false) }
            if (failed.isEmpty()) {
                _uiState.update { it.copy(statusMessage = strings.deletedItems(paths.size)) }
            } else {
                _uiState.update { it.copy(error = strings.batchDeleteFailed(failed.size, failed.first().error)) }
            }
            loadFiles()
        }
    }

    fun pullFile(remotePath: String, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferring = true, statusMessage = strings.downloadingFile(fileName), transferBytes = 0L, transferTotal = 0L) }
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            val localPath = "${downloadDir.absolutePath}/$fileName"
            val result = AdbService.pullFileWithProgress(remotePath, localPath) { copied, total ->
                _uiState.update { it.copy(transferBytes = copied, transferTotal = total) }
            }
            val size = formatFileSize(java.io.File(localPath).length())
            _uiState.update {
                it.copy(
                    isTransferring = false,
                    transferBytes = 0L,
                    transferTotal = 0L,
                    statusMessage = if (result.success) strings.downloadedFile(fileName, size, localPath) else strings.downloadFailedFmt(result.error)
                )
            }
        }
    }

    fun pushFile(localPath: String, fileName: String) {
        viewModelScope.launch {
            val size = formatFileSize(java.io.File(localPath).length())
            _uiState.update { it.copy(isTransferring = true, statusMessage = strings.uploadingFile(fileName, size), transferBytes = 0L, transferTotal = 0L) }
            val remotePath = "${_uiState.value.currentPath}/$fileName"
            val result = AdbService.pushFileWithProgress(localPath, remotePath) { copied, total ->
                _uiState.update { it.copy(transferBytes = copied, transferTotal = total) }
            }
            _uiState.update {
                it.copy(
                    isTransferring = false,
                    transferBytes = 0L,
                    transferTotal = 0L,
                    statusMessage = if (result.success) strings.uploadedFile(fileName, size) else strings.uploadFailedFmt(result.error)
                )
            }
            if (result.success) {
                loadFiles()
            } else {
                _uiState.update { it.copy(error = strings.uploadFailedFmt(result.error)) }
            }
        }
    }

    fun requestUpload() {
        _uiState.update { it.copy(requestFilePick = true) }
    }

    fun onFilePickHandled() {
        _uiState.update { it.copy(requestFilePick = false) }
    }

    fun showCreateDirDialog() {
        _uiState.update { it.copy(showCreateDirDialog = true) }
    }

    fun hideCreateDirDialog() {
        _uiState.update { it.copy(showCreateDirDialog = false) }
    }

    fun showPreview(path: String, name: String) {
        viewModelScope.launch {
            val lower = name.lowercase()
            val isImage = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").any { lower.endsWith(it) }
            val isText = listOf(
                ".txt", ".md", ".log", ".csv", ".json", ".xml", ".html", ".htm",
                ".kt", ".java", ".py", ".js", ".c", ".cpp", ".h", ".sh", ".gradle",
                ".properties", ".ini", ".cfg", ".yaml", ".yml", ".conf"
            ).any { lower.endsWith(it) }

            when {
                isImage -> {
                    _uiState.update { it.copy(isLoading = true) }
                    val local = File.createTempFile("preview_", "_${name}", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                    val result = AdbService.pullFile(path, local.absolutePath)
                    if (result.success) {
                        val bitmap = BitmapFactory.decodeFile(local.absolutePath)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                preview = FilePreview(path, name, FilePreviewType.IMAGE, bitmap = bitmap)
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                preview = FilePreview(path, name, FilePreviewType.IMAGE, error = result.error)
                            )
                        }
                    }
                }
                isText -> {
                    _uiState.update { it.copy(isLoading = true) }
                    val result = AdbService.readFilePreview(path, 100000)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            preview = FilePreview(
                                path, name, FilePreviewType.TEXT,
                                text = if (result.success) result.output else strings.previewFailed(result.error)
                            )
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(preview = FilePreview(path, name, FilePreviewType.UNSUPPORTED, error = strings.unsupportedFileType))
                    }
                }
            }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null) }
    }

    fun installApk(path: String, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = strings.installingFile(fileName)) }
            val result = AdbService.installApp(path)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = if (result.success) strings.installedFile(fileName) else strings.installFailedFmt(result.error)
                )
            }
        }
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
                _uiState.update { it.copy(error = strings.createFailed(result.error)) }
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val idx = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, idx.toDouble()), units[idx])
    }
}
