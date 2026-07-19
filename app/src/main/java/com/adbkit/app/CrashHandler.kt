package com.adbkit.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var context: Context

    fun install(ctx: Context) {
        if (this::context.isInitialized) return
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val report = buildString {
                appendLine("Crash report - $timestamp")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("Stack trace:")
                appendLine(throwable.stackTraceToString())
            }
            val crashDir = File(context.filesDir, "crashes")
            crashDir.mkdirs()
            val file = File(crashDir, "crash_${System.currentTimeMillis()}.log")
            file.writeText(report)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Crash logged: ${file.name}", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            // Ignore errors in crash handler
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }
}
