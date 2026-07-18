package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryInfo(
    val totalKb: Long = 0,
    val freeKb: Long = 0,
    val availableKb: Long = 0
) {
    val usedKb: Long get() = totalKb - availableKb
    val usedPercent: Float get() = if (totalKb > 0) usedKb.toFloat() / totalKb else 0f
}

data class ProcessDetails(
    val pid: String = "",
    val name: String = "",
    val commandLine: String = "",
    val threads: String = "",
    val ppid: String = "",
    val cpuTime: String = "",
    val residentPages: String = "",
    val error: String = ""
)

data class ProcessManagerUiState(
    val processes: List<Map<String, String>> = emptyList(),
    val runningApps: List<Map<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String = "",
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val statusMessage: String = "",
    val memoryInfo: MemoryInfo = MemoryInfo(),
    val showAppsOnly: Boolean = true,
    val sortMode: SortMode = SortMode.MEMORY,
    val sortAscending: Boolean = false,
    val processDetails: ProcessDetails? = null
) {
    enum class SortMode { MEMORY, PID, NAME, CPU }

    val filteredProcesses: List<Map<String, String>>
        get() = sorted(if (searchQuery.isEmpty()) processes else processes.filter {
            (it["name"] ?: "").contains(searchQuery, ignoreCase = true) ||
            (it["pid"] ?: "").contains(searchQuery)
        })
    val filteredApps: List<Map<String, String>>
        get() = sorted(if (searchQuery.isEmpty()) runningApps else runningApps.filter {
            (it["name"] ?: "").contains(searchQuery, ignoreCase = true)
        })

    private fun sorted(list: List<Map<String, String>>): List<Map<String, String>> {
        val key = when (sortMode) {
            SortMode.PID -> "pid"
            SortMode.NAME -> "name"
            SortMode.MEMORY -> "memory"
            SortMode.CPU -> "cpu"
        }
        val comparator = compareBy<Map<String, String>> {
            when (sortMode) {
                SortMode.PID -> (it[key] ?: "0").toLongOrNull() ?: 0L
                SortMode.MEMORY, SortMode.CPU -> (it[key] ?: "0").replace("%", "").toDoubleOrNull() ?: 0.0
                SortMode.NAME -> 0
            }
        }.thenBy { it["name"] ?: "" }
        return if (sortAscending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
    }
}

class ProcessManagerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProcessManagerUiState())
    val uiState: StateFlow<ProcessManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (AdbService.getCurrentDevice() == null) {
            _uiState.update { it.copy(error = "No device connected", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = "", statusMessage = "") }
        viewModelScope.launch {
            try {
                val memInfo = loadMemoryInfo()
                if (_uiState.value.showAppsOnly) {
                    val apps = AdbService.getRunningApps()
                    _uiState.update { it.copy(runningApps = apps, isLoading = false, memoryInfo = memInfo) }
                } else {
                    val processes = AdbService.getProcessList()
                    _uiState.update { it.copy(processes = processes, isLoading = false, memoryInfo = memInfo) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Load failed", isLoading = false) }
            }
        }
    }

    private suspend fun loadMemoryInfo(): MemoryInfo {
        val result = AdbService.shell("cat /proc/meminfo")
        if (!result.success) return MemoryInfo()
        var total = 0L
        var free = 0L
        var available = 0L
        result.output.lines().forEach { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val value = parts[1].toLongOrNull() ?: 0
                when {
                    line.startsWith("MemTotal:") -> total = value
                    line.startsWith("MemFree:") -> free = value
                    line.startsWith("MemAvailable:") -> available = value
                }
            }
        }
        return MemoryInfo(totalKb = total, freeKb = free, availableKb = available)
    }

    fun setShowAppsOnly(value: Boolean) {
        _uiState.update { it.copy(showAppsOnly = value) }
        refresh()
    }

    fun toggleSearch() {
        _uiState.update { it.copy(showSearch = !it.showSearch, searchQuery = "") }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortMode(mode: ProcessManagerUiState.SortMode) {
        _uiState.update {
            it.copy(
                sortMode = mode,
                sortAscending = if (it.sortMode == mode) !it.sortAscending else false
            )
        }
    }

    fun showProcessDetails(pid: String, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = AdbService.getProcessDetails(pid)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    processDetails = ProcessDetails(
                        pid = pid,
                        name = name,
                        commandLine = result["commandLine"] ?: "",
                        threads = result["threads"] ?: "",
                        ppid = result["ppid"] ?: "",
                        cpuTime = result["cpuTime"] ?: "",
                        residentPages = result["residentPages"] ?: "",
                        error = result["commandLine"].isNullOrEmpty().let { if (it) "Unable to read process details" else "" }
                    )
                )
            }
        }
    }

    fun dismissProcessDetails() {
        _uiState.update { it.copy(processDetails = null) }
    }

    fun killProcess(pid: String) {
        viewModelScope.launch {
            val result = AdbService.killProcess(pid)
            if (result.success) {
                _uiState.update { it.copy(statusMessage = "Process killed") }
                refresh()
            } else {
                _uiState.update { it.copy(statusMessage = "Kill failed: ${result.error}") }
            }
        }
    }

    fun forceStopApp(packageName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Stopping $packageName...") }
            val result = AdbService.forceStopApp(packageName)
            if (result.success) {
                _uiState.update { it.copy(statusMessage = "$packageName stopped") }
                refresh()
            } else {
                _uiState.update { it.copy(statusMessage = "Stop failed: ${result.error}") }
            }
        }
    }
}
