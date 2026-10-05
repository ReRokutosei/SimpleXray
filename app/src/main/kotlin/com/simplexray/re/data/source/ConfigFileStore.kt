package com.simplexray.re.data.source

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

private const val TAG = "ConfigFileStore"

internal class ConfigFileStore(
    private val application: Application,
    private val prefs: Preferences,
) {
    private fun getClipboardContent(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData: ClipData? = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item: ClipData.Item = clipData.getItemAt(0)
                val text: CharSequence? = item.text
                return text?.toString()
            }
        }
        return null
    }

    suspend fun createConfigFile(assets: AssetManager): String? {
        return withContext(Dispatchers.IO) {
            val filename = System.currentTimeMillis().toString() + ".json"
            val newFile = File(application.filesDir, filename)
            try {
                assets.open("template").use { input ->
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Created new config file: ${newFile.absolutePath}")
                newFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error creating new config file", e)
                return@withContext null
            }
        }
    }

    suspend fun importConfigFromClipboard(): String? {
        return withContext(Dispatchers.IO) {
            val clipboardContent = getClipboardContent(application)

            if (clipboardContent.isNullOrEmpty()) {
                Log.w(TAG, "Clipboard is empty, null, or does not contain text.")
                return@withContext null
            }
            importConfigFromContent(clipboardContent)
        }
    }

    suspend fun importConfigFromContent(content: String): String? {
        return withContext(Dispatchers.IO) {
            if (content.isEmpty()) {
                Log.w(TAG, "Content to import is empty.")
                return@withContext null
            }

            // This project only deals with full configs: the provided content must
            // be valid JSON or YAML. Share/subscription URIs are not supported.
            if (!ConfigUtils.isValidConfigContent(content)) {
                Log.e(TAG, "Rejected import: content is not a valid JSON/YAML config.")
                return@withContext null
            }
            val formattedContent = ConfigUtils.formatConfigContent(content)

            val filename = "imported_" + System.currentTimeMillis() + ".json"
            val newFile = File(application.filesDir, filename)

            try {
                FileOutputStream(newFile).use { fileOutputStream ->
                    fileOutputStream.write(formattedContent.toByteArray(StandardCharsets.UTF_8))
                }
                Log.d(
                    TAG,
                    "Successfully imported config from content to: ${newFile.absolutePath}"
                )
                newFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error saving imported config file from content.", e)
                return@withContext null
            }
        }
    }

    suspend fun deleteConfigFile(fileToDelete: File): Boolean {
        return withContext(Dispatchers.IO) {
            if (fileToDelete.delete()) {
                Log.d(TAG, "Successfully deleted config file: ${fileToDelete.name}")
                true
            } else {
                Log.e(TAG, "Failed to delete config file: ${fileToDelete.name}")
                false
            }
        }
    }

    suspend fun renameConfigFile(oldFile: File, newFile: File, newContent: String): Boolean =
        withContext(Dispatchers.IO) {
            if (oldFile.absolutePath == newFile.absolutePath) {
                try {
                    newFile.writeText(newContent)
                    Log.d(TAG, "Content updated for file: ${newFile.absolutePath}")
                    return@withContext true
                } catch (e: IOException) {
                    Log.e(TAG, "Error writing content to file: ${newFile.absolutePath}", e)
                    return@withContext false
                }
            }

            try {
                newFile.writeText(newContent)
                Log.d(TAG, "Content written to new file: ${newFile.absolutePath}")

                if (oldFile.exists()) {
                    val deleted = oldFile.delete()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete old config file: ${oldFile.absolutePath}")
                    }
                }

                val currentOrder = prefs.configFilesOrder.toMutableList()
                val oldName = oldFile.name
                val newName = newFile.name

                val oldNameIndex = currentOrder.indexOf(oldName)
                if (oldNameIndex != -1) {
                    currentOrder[oldNameIndex] = newName
                    prefs.configFilesOrder = currentOrder
                    Log.d(TAG, "Updated configFilesOrder: $oldName -> $newName")
                } else {
                    currentOrder.add(newName)
                    prefs.configFilesOrder = currentOrder
                    Log.w(TAG, "Old file name not found in order, adding new name to end: $newName")
                }

                if (prefs.selectedConfigPath == oldFile.absolutePath) {
                    prefs.selectedConfigPath = newFile.absolutePath
                    Log.d(
                        TAG,
                        "Updated selectedConfigPath: ${oldFile.absolutePath} -> ${newFile.absolutePath}"
                    )
                }

                return@withContext true
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Error renaming config file from ${oldFile.absolutePath} to ${newFile.absolutePath}",
                    e
                )
                if (newFile.exists()) {
                    newFile.delete()
                }
                return@withContext false
            }
        }

    suspend fun importConfigFileFromUri(context: Context, uri: Uri): String? {        return withContext(Dispatchers.IO) {
            try {
                var fileName = FileUriHelper.sanitizeFileName(
                    FileUriHelper.getFileNameFromUri(context, uri) ?: "imported_config.json",
                    fallback = "imported_config.json"
                )
                // Only full config files (.json/.yaml/.yml) are supported.
                if (!fileName.isConfigFile()) {
                    Log.e(TAG, "Rejected config import: unsupported file extension: $fileName")
                    return@withContext null
                }
                val extIndex = fileName.lastIndexOf('.')
                val nameWithoutExt = if (extIndex > 0) fileName.substring(0, extIndex) else fileName
                val ext = if (extIndex > 0) fileName.substring(extIndex) else ".json"

                var targetFile = File(application.filesDir, fileName)
                var count = 1
                while (targetFile.exists()) {
                    fileName = "$nameWithoutExt ($count)$ext"
                    targetFile = File(application.filesDir, fileName)
                    count++
                }

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                targetFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error importing config from URI", e)
                null
            }
        }
    }
}
