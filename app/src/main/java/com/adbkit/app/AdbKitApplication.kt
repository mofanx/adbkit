package com.adbkit.app

import android.app.Application
import android.util.Log
import com.adbkit.app.data.SettingsRepository
import com.adbkit.app.service.AdbBinaryManager
import com.adbkit.app.service.AdbService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdbKitApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        initAdbPath()
    }

    private fun initAdbPath() {
        appScope.launch(Dispatchers.IO) {
            // 1. Try to extract bundled binary first
            try {
                val (adbPath, fastbootPath) = AdbBinaryManager.setup(instance)
                AdbService.setAdbPath(adbPath)
                AdbService.setFastbootPath(fastbootPath)
                Log.i("AdbKit", "Bundled ADB: $adbPath")
            } catch (e: Exception) {
                Log.w("AdbKit", "Bundled ADB setup failed: ${e.message}")
            }

            // 2. Override with user-saved path if explicitly set
            val repo = SettingsRepository(instance)
            val savedPath = repo.adbPath.first()
            if (savedPath != "adb" && savedPath.isNotBlank()) {
                AdbService.setAdbPath(savedPath)
            }
            val savedFastboot = repo.fastbootPath.first()
            if (savedFastboot != "fastboot" && savedFastboot.isNotBlank()) {
                AdbService.setFastbootPath(savedFastboot)
            }
        }
    }

    companion object {
        lateinit var instance: AdbKitApplication
            private set
    }
}
