package com.reilandeubank.unprocess.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatManager {

    private const val LOG_DIR = "logs"
    private const val MAX_LOGS = 5

    private var logProcess: Process? = null
    private var activeLogFile: File? = null

    val isLogging: Boolean get() = logProcess?.isAlive == true

    fun start(context: Context) {
        if (isLogging) return

        val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        pruneOldLogs(logDir)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(logDir, "filmr_log_$timestamp.txt")
        activeLogFile = file

        val pid = android.os.Process.myPid().toString()
        logProcess = ProcessBuilder(
            "logcat",
            "--pid=$pid",
            "-v", "time",
            "-b", "all"   // include main, crash, system buffers
        )
            .redirectOutput(file)
            .redirectErrorStream(true)
            .start()
    }

    fun stop() {
        logProcess?.destroy()
        logProcess = null
    }

    fun latestLogFile(context: Context): File? {
        val logDir = File(context.filesDir, LOG_DIR)
        return logDir.listFiles { f -> f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun logFileSize(context: Context): String {
        val f = latestLogFile(context) ?: return ""
        val kb = f.length() / 1024
        return if (kb < 1024) "${kb} KB" else "${"%.1f".format(kb / 1024.0)} MB"
    }

    private fun pruneOldLogs(logDir: File) {
        val logs = logDir.listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: return
        logs.drop(MAX_LOGS - 1).forEach { it.delete() }
    }
}
