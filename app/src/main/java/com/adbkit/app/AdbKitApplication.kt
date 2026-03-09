package com.adbkit.app

import android.app.Application
import com.adbkit.app.data.SettingsRepository
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
            val repo = SettingsRepository(instance)
            val savedPath = repo.adbPath.first()
            AdbService.setAdbPath(savedPath)
            val savedFastboot = repo.fastbootPath.first()
            AdbService.setFastbootPath(savedFastboot)
        }
    }

    companion object {
        lateinit var instance: AdbKitApplication
            private set
    }
}
