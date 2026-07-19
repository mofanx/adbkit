package com.adbkit.app.data

import android.content.Context
import com.adbkit.app.AdbKitApplication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScriptMacro(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val commands: List<String> = emptyList()
)

class MacroRepository(context: Context = AdbKitApplication.instance) {
    private val file = File(context.filesDir, "terminal_macros.json")
    private val gson = Gson()

    suspend fun save(macro: ScriptMacro) {
        withContext(Dispatchers.IO) {
            val list = loadSync().toMutableList()
            list.removeAll { it.id == macro.id }
            list.add(0, macro)
            while (list.size > 30) list.removeAt(list.lastIndex)
            file.writeText(gson.toJson(list))
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val list = loadSync().filter { it.id != id }
            file.writeText(gson.toJson(list))
        }
    }

    suspend fun load(): List<ScriptMacro> = withContext(Dispatchers.IO) { loadSync() }

    private fun loadSync(): List<ScriptMacro> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<ScriptMacro>>() {}.type
            gson.fromJson<List<ScriptMacro>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
