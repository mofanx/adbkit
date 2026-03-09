package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProcessManagerUiState(
    val processes: List<Map<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String = "",
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val statusMessage: String = ""
) {
    val filteredProcesses: List<Map<String, String>>
        get() = if (searchQuery.isEmpty()) processes
        else processes.filter {
            (it["name"] ?: "").contains(searchQuery, ignoreCase = true) ||
            (it["pid"] ?: "").contains(searchQuery)
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
        _uiState.update { it.copy(isLoading = true, error = "") }
        viewModelScope.launch {
            try {
                val processes = AdbService.getProcessList()
                _uiState.update { it.copy(processes = processes, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Load failed", isLoading = false) }
            }
        }
    }

    fun toggleSearch() {
        _uiState.update { it.copy(showSearch = !it.showSearch, searchQuery = "") }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun killProcess(pid: String) {
        viewModelScope.launch {
            val result = AdbService.killProcess(pid)
            if (result.success) {
                refresh()
            } else {
                _uiState.update { it.copy(statusMessage = "Kill failed: ${result.error}") }
            }
        }
    }
}
