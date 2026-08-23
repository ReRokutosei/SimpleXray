package com.simplexray.re.data.source

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.RandomAccessFile

class LogFileManager(context: Context) {
    val logFile: File

    init {
        val filesDir = context.filesDir
        this.logFile = File(filesDir, LOG_FILE_NAME)
        Log.d(TAG, "Log file path: " + logFile.absolutePath)
    }

    @Synchronized
    fun appendLog(logEntry: String?) {
        try {
            FileWriter(logFile, true).use { fileWriter ->
                PrintWriter(fileWriter).use { printWriter ->
                    if (logEntry != null) {
                        printWriter.println(logEntry)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error appending log to file", e)
        } finally {
            checkAndTruncateLogFile()
        }
    }

    fun readLogs(): String? {
        if (!logFile.exists()) {
            Log.d(TAG, "Log file does not exist.")
            return ""
        }
        return try {
            // Read only the tail of the file: the file can grow up to
            // MAX_LOG_SIZE_BYTES and the UI only ever shows the last
            // MAX_LOG_ENTRIES, so reading everything would waste memory/CPU.
            RandomAccessFile(logFile, "r").use { raf ->
                val fileSize = raf.length()
                if (fileSize == 0L) {
                    return ""
                }
                val readStart = (fileSize - READ_TAIL_BYTES).coerceAtLeast(0L)
                raf.seek(readStart)
                val bytes = ByteArray((fileSize - readStart).toInt())
                raf.readFully(bytes)
                var content = String(bytes, Charsets.UTF_8)
                if (readStart > 0L) {
                    // Drop the possibly half-cut first line from the tail window.
                    val firstNewline = content.indexOf('\n')
                    if (firstNewline > 0) {
                        content = content.substring(firstNewline + 1)
                    }
                }
                content
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading log file", e)
            null
        }
    }

    @Synchronized
    fun clearLogs() {
        if (logFile.exists()) {
            try {
                FileWriter(logFile, false).use { fileWriter ->
                    fileWriter.write("")
                    Log.d(TAG, "Log file content cleared successfully.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to clear log file content.", e)
            }
        } else {
            Log.d(TAG, "Log file does not exist, no content to clear.")
        }
    }

    @Synchronized
    private fun checkAndTruncateLogFile() {
        if (!logFile.exists()) {
            Log.d(TAG, "Log file does not exist for truncation check.")
            return
        }
        val currentSize = logFile.length()
        if (currentSize <= MAX_LOG_SIZE_BYTES) {
            return
        }
        Log.d(
            TAG,
            "Log file size ($currentSize bytes) exceeds limit ($MAX_LOG_SIZE_BYTES bytes). Truncating oldest $TRUNCATE_SIZE_BYTES bytes."
        )
        try {
            val startByteToKeep = currentSize - TRUNCATE_SIZE_BYTES
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.seek(startByteToKeep)
                val firstLineToKeepStartPos: Long
                val firstPartialOrFullLine = raf.readLine()
                if (firstPartialOrFullLine != null) {
                    firstLineToKeepStartPos = raf.filePointer
                } else {
                    Log.w(
                        TAG,
                        "Could not read line from calculated start position for truncation. Clearing file as a fallback."
                    )
                    clearLogs()
                    return
                }
                raf.channel.use { sourceChannel ->
                    val tempLogFile = File(logFile.parentFile, "$LOG_FILE_NAME.tmp")
                    FileOutputStream(tempLogFile).use { fos ->
                        fos.channel.use { destChannel ->
                            val bytesToTransfer = sourceChannel.size() - firstLineToKeepStartPos
                            sourceChannel.transferTo(
                                firstLineToKeepStartPos,
                                bytesToTransfer,
                                destChannel
                            )
                        }
                    }
                    if (logFile.delete()) {
                        if (tempLogFile.renameTo(logFile)) {
                            Log.d(
                                TAG,
                                "Log file truncated successfully. New size: " + logFile.length() + " bytes."
                            )
                        } else {
                            Log.e(TAG, "Failed to rename temp log file to original file.")
                            tempLogFile.delete()
                        }
                    } else {
                        Log.e(TAG, "Failed to delete original log file during truncation.")
                        tempLogFile.delete()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error during log file truncation", e)
            clearLogs()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during log file truncation", e)
            clearLogs()
        }
    }

    companion object {
        private const val TAG = "LogFileManager"
        private const val LOG_FILE_NAME = "app_log.txt"
        private const val MAX_LOG_SIZE_BYTES = (5 * 1024 * 1024).toLong()
        private const val TRUNCATE_SIZE_BYTES = (2560 * 1024).toLong()
        private const val READ_TAIL_BYTES = (1 * 1024 * 1024).toLong()
    }
}
