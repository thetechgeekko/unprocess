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

    val isLogging: Boolean get() = logProcess?.isAlive == true

    fun start(context: Context) {
        if (isLogging) return

        val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        pruneOldLogs(logDir)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(logDir, "filmr_log_$timestamp.txt")

        val pid = android.os.Process.myPid().toString()
        logProcess = ProcessBuilder(
            "logcat", "--pid=$pid", "-v", "time", "-b", "all"
        )
            .redirectOutput(file)
            .redirectErrorStream(true)
            .start()
    }

    fun stop() {
        logProcess?.destroy()
        logProcess = null
    }

    fun latestLogFile(context: Context): File? =
        File(context.filesDir, LOG_DIR)
            .listFiles { f -> f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }

    fun logFileSize(context: Context): String {
        val kb = (latestLogFile(context)?.length() ?: 0L) / 1024
        return if (kb < 1024) "${kb} KB" else "${"%.1f".format(kb / 1024.0)} MB"
    }

    private fun pruneOldLogs(logDir: File) {
        logDir.listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS - 1)
            ?.forEach { it.delete() }
    }
}
