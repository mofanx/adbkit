package com.adbkit.app

import android.app.Application

class AdbKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AdbKitApplication
            private set
    }
}
