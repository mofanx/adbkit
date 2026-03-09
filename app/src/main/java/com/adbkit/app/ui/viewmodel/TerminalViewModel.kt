package com.adbkit.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalUiState(
    val currentCommand: String = "",
    val outputLines: List<String> = listOf("--- ADB Kit Terminal ---", "输入命令开始执行"),
    val isShellMode: Boolean = true,
    val isExecuting: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val showHistory: Boolean = false,
    val currentDevice: String? = null
)

class TerminalViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState(currentDevice = AdbService.getCurrentDevice()))
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun setCommand(cmd: String) {
        _uiState.update { it.copy(currentCommand = cmd, showHistory = false) }
    }

    fun toggleShellMode() {
        _uiState.update {
            val newMode = !it.isShellMode
            val modeText = if (newMode) "--- 切换到 ADB Shell 模式 ---" else "--- 切换到 ADB 命令模式 ---"
            it.copy(isShellMode = newMode, outputLines = it.outputLines + modeText)
        }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun clearOutput() {
        _uiState.update { it.copy(outputLines = listOf("--- 已清屏 ---")) }
    }

    fun executeCommand() {
        val cmd = _uiState.value.currentCommand.trim()
        if (cmd.isBlank()) return

        val prefix = if (_uiState.value.isShellMode) "$ " else ">>> adb "
        _uiState.update {
            it.copy(
                isExecuting = true,
                outputLines = it.outputLines + "$prefix$cmd",
                currentCommand = "",
                commandHistory = (it.commandHistory + cmd).distinct().takeLast(50)
            )
        }

        viewModelScope.launch {
            val result = if (_uiState.value.isShellMode) {
                AdbService.shell(cmd)
            } else {
                AdbService.adb(cmd)
            }

            val outputLines = mutableListOf<String>()
            if (result.output.isNotEmpty()) {
                outputLines.addAll(result.output.lines())
            }
            if (result.error.isNotEmpty()) {
                outputLines.addAll(result.error.lines().map { "错误: $it" })
            }
            if (outputLines.isEmpty()) {
                outputLines.add(if (result.success) "(命令执行成功，无输出)" else "(命令执行失败)")
            }

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    outputLines = it.outputLines + outputLines
                )
            }
        }
    }

    fun executeQuickCommand(cmd: String) {
        _uiState.update { it.copy(currentCommand = cmd) }
        executeCommand()
    }
}
