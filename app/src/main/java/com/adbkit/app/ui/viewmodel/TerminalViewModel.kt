package com.adbkit.app.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adbkit.app.AdbKitApplication
import com.adbkit.app.data.MacroRepository
import com.adbkit.app.data.ScriptMacro
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.service.AdbService
import com.adbkit.app.service.CommandResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalUiState(
    val currentCommand: String = "",
    val outputLines: List<String> = listOf("--- ADB Kit Terminal ---"),
    val isShellMode: Boolean = true,
    val isExecuting: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val commandFavorites: List<String> = emptyList(),
    val macros: List<ScriptMacro> = emptyList(),
    val showMacros: Boolean = false,
    val macroName: String = "",
    val showHistory: Boolean = false,
    val showFavorites: Boolean = false,
    val searchQuery: String = "",
    val currentDevice: String? = null
) {
    val filteredOutputLines: List<String>
        get() = if (searchQuery.isBlank()) outputLines
        else outputLines.filter { it.contains(searchQuery, ignoreCase = true) }
}

class TerminalViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState(currentDevice = AdbService.getCurrentDevice()))
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()
    private val repo = SettingsRepository(AdbKitApplication.instance)
    private val macroRepo = MacroRepository()
    private var saveHistoryEnabled = true
    private var currentCommandJob: Job? = null

    init {
        viewModelScope.launch {
            repo.commandHistory.collect { history ->
                _uiState.update { it.copy(commandHistory = history) }
            }
        }
        viewModelScope.launch {
            repo.commandFavorites.collect { favorites ->
                _uiState.update { it.copy(commandFavorites = favorites) }
            }
        }
        viewModelScope.launch {
            repo.saveHistory.collect { saveHistory ->
                saveHistoryEnabled = saveHistory
            }
        }
        loadMacros()
    }

    private fun loadMacros() {
        viewModelScope.launch {
            _uiState.update { it.copy(macros = macroRepo.load()) }
        }
    }

    fun setCommand(cmd: String) {
        _uiState.update { it.copy(currentCommand = cmd, showHistory = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun copyAllOutput(context: Context): String {
        val text = _uiState.value.outputLines.joinToString("\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Terminal output", text)
        clipboard.setPrimaryClip(clip)
        return text
    }

    fun toggleShellMode() {
        _uiState.update {
            val newMode = !it.isShellMode
            val modeText = if (newMode) "--- ADB Shell Mode ---" else "--- ADB Command Mode ---"
            it.copy(isShellMode = newMode, outputLines = it.outputLines + modeText)
        }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory, showFavorites = false) }
    }

    fun toggleFavorites() {
        _uiState.update { it.copy(showFavorites = !it.showFavorites, showHistory = false) }
    }

    fun toggleMacros() {
        _uiState.update { it.copy(showMacros = !it.showMacros, showHistory = false, showFavorites = false) }
    }

    fun setMacroName(name: String) {
        _uiState.update { it.copy(macroName = name) }
    }

    fun saveMacro(name: String, commands: List<String>) {
        if (name.isBlank() || commands.isEmpty()) return
        viewModelScope.launch {
            macroRepo.save(ScriptMacro(name = name, commands = commands))
            _uiState.update { it.copy(macros = macroRepo.load(), macroName = "") }
        }
    }

    fun deleteMacro(id: String) {
        viewModelScope.launch {
            macroRepo.delete(id)
            _uiState.update { it.copy(macros = macroRepo.load()) }
        }
    }

    fun runMacro(macro: ScriptMacro) {
        val commands = macro.commands
        if (commands.isEmpty()) {
            _uiState.update { it.copy(outputLines = it.outputLines + "Macro '${macro.name}' has no commands") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, outputLines = it.outputLines + "--- Running macro '${macro.name}' (${commands.size} commands) ---") }
            commands.forEach { cmd ->
                val prefix = if (_uiState.value.isShellMode) "$ " else ">>> adb "
                _uiState.update { it.copy(outputLines = it.outputLines + "$prefix$cmd") }
                val result = try {
                    if (_uiState.value.isShellMode) AdbService.shell(cmd) else AdbService.adb(cmd)
                } catch (e: Exception) {
                    CommandResult(false, "", e.message ?: "Error", -1)
                }
                val outLines = mutableListOf<String>()
                if (result.output.isNotEmpty()) outLines.addAll(result.output.lines())
                if (result.error.isNotEmpty()) outLines.addAll(result.error.lines().map { "ERR: $it" })
                if (outLines.isEmpty()) outLines.add(if (result.success) "(OK, no output)" else "(FAILED)")
                _uiState.update { it.copy(outputLines = it.outputLines + outLines) }
            }
            _uiState.update { it.copy(isExecuting = false, outputLines = it.outputLines + "--- Macro finished ---") }
        }
    }

    fun addFavorite(command: String) {
        viewModelScope.launch {
            repo.addCommandFavorite(command)
        }
    }

    fun removeFavorite(command: String) {
        viewModelScope.launch {
            repo.removeCommandFavorite(command)
        }
    }

    fun clearOutput() {
        _uiState.update { it.copy(outputLines = listOf("--- Cleared ---")) }
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

        currentCommandJob?.cancel()
        currentCommandJob = viewModelScope.launch {
            if (saveHistoryEnabled) {
                repo.addCommandHistory(cmd)
            }
            val result = try {
                if (_uiState.value.isShellMode) {
                    AdbService.shell(cmd)
                } else {
                    AdbService.adb(cmd)
                }
            } catch (e: Exception) {
                CommandResult(false, "", e.message ?: "Cancelled", -1)
            }

            val outputLines = mutableListOf<String>()
            if (result.output.isNotEmpty()) {
                outputLines.addAll(result.output.lines())
            }
            if (result.error.isNotEmpty()) {
                outputLines.addAll(result.error.lines().map { "ERR: $it" })
            }
            if (outputLines.isEmpty()) {
                outputLines.add(if (result.success) "(OK, no output)" else "(FAILED)")
            }
            if (!result.success && result.exitCode == -1 && result.error.contains("Cancelled")) {
                outputLines.add("(CANCELLED)")
            }

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    outputLines = it.outputLines + outputLines
                )
            }
        }
    }

    fun cancelCommand() {
        currentCommandJob?.cancel()
        currentCommandJob = null
    }

    fun executeQuickCommand(cmd: String) {
        _uiState.update { it.copy(currentCommand = cmd) }
        executeCommand()
    }

    fun saveOutput() {
        viewModelScope.launch {
            val text = _uiState.value.outputLines.joinToString("\n")
            val result = AdbService.saveOutputLog(text, "adbkit_terminal_log.txt")
            _uiState.update {
                it.copy(
                    outputLines = it.outputLines + if (result.success) "Output saved: ${result.output}" else "Save failed: ${result.error}"
                )
            }
        }
    }

    fun runFavoritesScript() {
        val favorites = _uiState.value.commandFavorites
        if (favorites.isEmpty()) {
            _uiState.update { it.copy(outputLines = it.outputLines + "No favorite commands to run") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, outputLines = it.outputLines + "--- Running favorite script (${favorites.size} commands) ---") }
            favorites.forEach { cmd ->
                val prefix = if (_uiState.value.isShellMode) "$ " else ">>> adb "
                _uiState.update { it.copy(outputLines = it.outputLines + "$prefix$cmd") }
                val result = try {
                    if (_uiState.value.isShellMode) AdbService.shell(cmd) else AdbService.adb(cmd)
                } catch (e: Exception) {
                    CommandResult(false, "", e.message ?: "Error", -1)
                }
                val outLines = mutableListOf<String>()
                if (result.output.isNotEmpty()) outLines.addAll(result.output.lines())
                if (result.error.isNotEmpty()) outLines.addAll(result.error.lines().map { "ERR: $it" })
                if (outLines.isEmpty()) outLines.add(if (result.success) "(OK, no output)" else "(FAILED)")
                _uiState.update { it.copy(outputLines = it.outputLines + outLines) }
            }
            _uiState.update { it.copy(isExecuting = false, outputLines = it.outputLines + "--- Script finished ---") }
        }
    }

    fun shareFavoritesScript(context: Context) {
        val favorites = _uiState.value.commandFavorites
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Generated by AdbKit")
            if (favorites.isEmpty()) {
                appendLine("# No favorite commands")
            } else {
                favorites.forEach { appendLine(it) }
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AdbKit command script")
            putExtra(Intent.EXTRA_TEXT, script)
        }
        val chooser = Intent.createChooser(intent, "Share script")
        context.startActivity(chooser)
    }
}
