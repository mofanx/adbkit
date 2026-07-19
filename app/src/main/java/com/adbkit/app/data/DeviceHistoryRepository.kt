package com.adbkit.app.data

import android.content.Context
import com.adbkit.app.AdbKitApplication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceInfoSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: String = "",
    val batteryTemp: String = "",
    val batteryStatus: String = "",
    val totalStorage: Long = 0,
    val usedStorage: Long = 0,
    val availableStorage: Long = 0
) {
    val timeText: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

class DeviceHistoryRepository(context: Context = AdbKitApplication.instance) {
    private val file = File(context.filesDir, "device_info_history.json")
    private val gson = Gson()

    suspend fun add(snapshot: DeviceInfoSnapshot) {
        withContext(Dispatchers.IO) {
            val list = loadSync().toMutableList()
            list.add(0, snapshot)
            if (list.size > 30) list.subList(30, list.size).clear()
            saveSync(list)
        }
    }

    suspend fun load(): List<DeviceInfoSnapshot> = withContext(Dispatchers.IO) { loadSync() }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            file.delete()
        }
    }

    private fun loadSync(): List<DeviceInfoSnapshot> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<DeviceInfoSnapshot>>() {}.type
            gson.fromJson<List<DeviceInfoSnapshot>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSync(list: List<DeviceInfoSnapshot>) {
        file.writeText(gson.toJson(list))
    }
}
