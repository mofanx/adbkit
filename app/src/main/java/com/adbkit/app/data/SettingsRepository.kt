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
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val CONFIRM_DANGEROUS = booleanPreferencesKey("confirm_dangerous")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val LAST_DEVICE_IP = stringPreferencesKey("last_device_ip")
    }

    val adbPath: Flow<String> = context.dataStore.data.map { it[ADB_PATH] ?: "adb" }
    val fastbootPath: Flow<String> = context.dataStore.data.map { it[FASTBOOT_PATH] ?: "fastboot" }
    val defaultPort: Flow<String> = context.dataStore.data.map { it[DEFAULT_PORT] ?: "5555" }
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEEP_SCREEN_ON] ?: false }
    val confirmDangerous: Flow<Boolean> = context.dataStore.data.map { it[CONFIRM_DANGEROUS] ?: true }
    val saveHistory: Flow<Boolean> = context.dataStore.data.map { it[SAVE_HISTORY] ?: true }
    val lastDeviceIp: Flow<String> = context.dataStore.data.map { it[LAST_DEVICE_IP] ?: "" }

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

    suspend fun setDarkMode(value: Boolean) {
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
}
