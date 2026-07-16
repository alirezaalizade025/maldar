package com.personalfinance.tracker.util

import android.content.Context
import android.os.Build
import com.personalfinance.tracker.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions (and manual log entries) to a file on disk so the
 * user can send the log even when the app crashes with no visible error.
 *
 * Install via [install] from Application.onCreate(). Retrieval via [getLogText]
 * and [clear] from a diagnostics screen.
 */
object CrashLogger {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_LINES = 400

    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
    }

    fun install(context: Context) {
        init(context)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("UNCAUGHT EXCEPTION on thread=${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    fun log(message: String, throwable: Throwable? = null) {
        if (!::file.isInitialized) return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
            .append("[$stamp] $message\n")
        throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString()).append("\n")
        }
        val existing = try { file.readText() } catch (_: Exception) { "" }
        val lines = (existing + sb.toString()).lineSequence().toList()
        val trimmed = if (lines.size > MAX_LINES) lines.takeLast(MAX_LINES) else lines
        try { file.writeText(trimmed.joinToString("\n")) } catch (_: Exception) { }
    }

    fun getLogText(): String = if (::file.isInitialized) {
        try { file.readText() } catch (_: Exception) { "" }
    } else ""

    @Synchronized
    fun clear() {
        if (::file.isInitialized) try { file.writeText("") } catch (_: Exception) { }
    }

    val deviceInfo: String
        get() = buildString {
            append("Model: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("SDK: ${Build.VERSION.SDK_INT}\n")
            append("Android: ${Build.VERSION.RELEASE}\n")
            append("App: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        }
}
