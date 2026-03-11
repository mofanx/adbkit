package com.adbkit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val ADB_PATH = stringPreferencesKey("adb_path")
        val FASTBOOT_PATH = stringPreferencesKey("fastboot_path")
        val DEFAULT_PORT = stringPreferencesKey("default_port")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val CONFIRM_DANGEROUS = booleanPreferencesKey("confirm_dangerous")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val LAST_DEVICE_IP = stringPreferencesKey("last_device_ip")
        val CONNECTION_HISTORY = stringPreferencesKey("connection_history")
        val LANGUAGE = stringPreferencesKey("language")
        val DEVICE_CLICK_TARGET = stringPreferencesKey("device_click_target")
    }

    val adbPath: Flow<String> = context.dataStore.data.map { it[ADB_PATH] ?: "adb" }
    val fastbootPath: Flow<String> = context.dataStore.data.map { it[FASTBOOT_PATH] ?: "fastboot" }
    val defaultPort: Flow<String> = context.dataStore.data.map { it[DEFAULT_PORT] ?: "5555" }
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val darkMode: Flow<String> = context.dataStore.data.map { it[DARK_MODE] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEEP_SCREEN_ON] ?: false }
    val confirmDangerous: Flow<Boolean> = context.dataStore.data.map { it[CONFIRM_DANGEROUS] ?: true }
    val saveHistory: Flow<Boolean> = context.dataStore.data.map { it[SAVE_HISTORY] ?: true }
    val lastDeviceIp: Flow<String> = context.dataStore.data.map { it[LAST_DEVICE_IP] ?: "" }
    val connectionHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[CONNECTION_HISTORY] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }
    val language: Flow<String> = context.dataStore.data.map { it[LANGUAGE] ?: "zh" }
    val deviceClickTarget: Flow<String> = context.dataStore.data.map { it[DEVICE_CLICK_TARGET] ?: "device_info" }

    suspend fun setAdbPath(path: String) {
        context.dataStore.edit { it[ADB_PATH] = path }
    }

    suspend fun setFastbootPath(path: String) {
        context.dataStore.edit { it[FASTBOOT_PATH] = path }
    }

    suspend fun setDefaultPort(port: String) {
        context.dataStore.edit { it[DEFAULT_PORT] = port }
    }

    suspend fun setAutoConnect(value: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = value }
    }

    suspend fun setDarkMode(value: String) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[KEEP_SCREEN_ON] = value }
    }

    suspend fun setConfirmDangerous(value: Boolean) {
        context.dataStore.edit { it[CONFIRM_DANGEROUS] = value }
    }

    suspend fun setSaveHistory(value: Boolean) {
        context.dataStore.edit { it[SAVE_HISTORY] = value }
    }

    suspend fun setLastDeviceIp(ip: String) {
        context.dataStore.edit { it[LAST_DEVICE_IP] = ip }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE] = lang }
    }

    suspend fun addConnectionHistory(address: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONNECTION_HISTORY] ?: ""
            val list = if (raw.isBlank()) mutableListOf() else raw.split(",").filter { it.isNotBlank() }.toMutableList()
            list.remove(address)
            list.add(0, address)
            if (list.size > 20) list.subList(20, list.size).clear()
            prefs[CONNECTION_HISTORY] = list.joinToString(",")
        }
    }

    suspend fun removeConnectionHistory(address: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONNECTION_HISTORY] ?: ""
            val list = raw.split(",").filter { it.isNotBlank() && it != address }
            prefs[CONNECTION_HISTORY] = list.joinToString(",")
        }
    }

    suspend fun setDeviceClickTarget(target: String) {
        context.dataStore.edit { it[DEVICE_CLICK_TARGET] = target }
    }
}
