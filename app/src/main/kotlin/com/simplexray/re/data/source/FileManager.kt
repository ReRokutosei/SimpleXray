package com.simplexray.re.data.source

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import com.simplexray.re.R
import com.simplexray.re.common.ConfigUtils
import com.simplexray.re.common.isConfigFile
import com.simplexray.re.common.FilenameValidator
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.log10
import kotlin.math.pow

class FileManager(private val application: Application, private val prefs: Preferences) {
    @Throws(IOException::class)
    private fun readFileContent(file: File): String {
        return file.readText(StandardCharsets.UTF_8)
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun calculateSha256(`is`: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024)
        var read: Int
        `is`.use { inputStream ->
            while ((inputStream.read(buffer).also { read = it }) != -1) {
                digest.update(buffer, 0, read)
            }
        }

        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (hashByte in hashBytes) {
            sb.append(String.format("%02x", hashByte))
        }
        return sb.toString()
    }

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
                val fileContent: String = assets.open("template").use { assetInputStream ->
                    val size = assetInputStream.available()
                    val buffer = ByteArray(size)
                    assetInputStream.read(buffer)
                    String(buffer, StandardCharsets.UTF_8)
                }
                FileOutputStream(newFile).use { fileOutputStream ->
                    fileOutputStream.write(fileContent.toByteArray())
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


    fun extractAssetsIfNeeded() {
        val files = arrayOf("geoip.dat", "geosite.dat")
        val dir = application.filesDir
        dir.mkdirs()
        for (file in files) {
            val targetFile = File(dir, file)
            var needsExtraction = false

            val isCustomImported =
                if (file == "geoip.dat") prefs.customGeoipImported else prefs.customGeositeImported

            if (isCustomImported) {
                Log.d(TAG, "Custom file already imported for $file, skipping asset extraction.")
                continue
            }

            if (targetFile.exists()) {
                try {
                    val existingFileHash =
                        calculateSha256(Files.newInputStream(targetFile.toPath()))
                    val assetHash = calculateSha256(application.assets.open(file))
                    if (existingFileHash != assetHash) {
                        needsExtraction = true
                    }
                } catch (e: IOException) {
                    needsExtraction = true
                    Log.d(TAG, e.toString())
                } catch (e: NoSuchAlgorithmException) {
                    needsExtraction = true
                    Log.d(TAG, e.toString())
                }
            } else {
                needsExtraction = true
            }
            if (needsExtraction) {
                try {
                    application.assets.open(file).use { `in` ->
                        FileOutputStream(targetFile).use { out ->
                            val buffer = ByteArray(1024)
                            var read: Int
                            while ((`in`.read(buffer).also { read = it }) != -1) {
                                out.write(buffer, 0, read)
                            }
                            Log.d(
                                TAG,
                                "Extracted asset: " + file + " to " + targetFile.absolutePath
                            )
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException("Failed to extract asset: $file", e)
                }
            } else {
                Log.d(TAG, "Asset $file already exists and matches hash, skipping extraction.")
            }
        }
    }

    suspend fun importRuleFile(uri: Uri, filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = File(application.filesDir, filename)
            val tempFile = File(application.filesDir, "$filename.tmp")
            try {
                application.contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        if (inputStream == null) {
                            throw IOException("Failed to open input stream for URI: $uri")
                        }
                        val buffer = ByteArray(4096)
                        var read: Int
                        while ((inputStream.read(buffer).also { read = it }) != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
                if (com.simplexray.re.common.GeoDataValidator.validateDatFile(application, tempFile, filename)) {
                    if (tempFile.renameTo(targetFile)) {
                        when (filename) {
                            "geoip.dat" -> prefs.customGeoipImported = true
                            "geosite.dat" -> prefs.customGeositeImported = true
                        }
                        Log.d(TAG, "Successfully imported $filename from URI: $uri")
                        return@withContext true
                    }
                }
                tempFile.delete()
                false
            } catch (e: Exception) {
                tempFile.delete()
                if (filename == "geoip.dat") {
                    prefs.customGeoipImported = false
                } else if (filename == "geosite.dat") {
                    prefs.customGeositeImported = false
                }
                Log.e(TAG, "Error importing rule file: $filename", e)
                false
            }
        }
    }

    suspend fun saveRuleFile(
        inputStream: InputStream,
        filename: String,
        onProgress: (Int) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = File(application.filesDir, filename)
            val tempFile = File(application.filesDir, "$filename.tmp")
            try {
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        onProgress(read)
                    }
                }

                if (com.simplexray.re.common.GeoDataValidator.validateDatFile(application, tempFile, filename)) {
                    if (tempFile.renameTo(targetFile)) {
                        when (filename) {
                            "geoip.dat" -> prefs.customGeoipImported = true
                            "geosite.dat" -> prefs.customGeositeImported = true
                        }
                        Log.d(TAG, "Successfully saved $filename from stream")
                        return@withContext true
                    }
                }
                Log.e(TAG, "Validation failed or rename failed for $filename")
                tempFile.delete()
                false
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "Unexpected error during rule file save: $filename", e)
                false
            }
        }
    }

    /**
     * Validate a pre-downloaded temp dat file with sandbox, then atomically rename to target.
     * Used by [com.simplexray.re.service.GeoUpdateWorker] for background auto-updates.
     */
    suspend fun saveRuleFileFromTemp(tempFile: java.io.File, filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = File(application.filesDir, filename)
            try {
                if (!com.simplexray.re.common.GeoDataValidator.validateDatFile(application, tempFile, filename)) {
                    Log.e(TAG, "saveRuleFileFromTemp: validation failed for $filename")
                    tempFile.delete()
                    return@withContext false
                }
                if (tempFile.renameTo(targetFile)) {
                    when (filename) {
                        "geoip.dat" -> prefs.customGeoipImported = true
                        "geosite.dat" -> prefs.customGeositeImported = true
                    }
                    Log.d(TAG, "saveRuleFileFromTemp: successfully updated $filename")
                    true
                } else {
                    tempFile.delete()
                    Log.e(TAG, "saveRuleFileFromTemp: rename failed for $filename")
                    false
                }
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "saveRuleFileFromTemp: error for $filename", e)
                false
            }
        }
    }

    fun getRuleFileSummary(filename: String): String {
        Log.d(TAG, "getRuleFileSummary called with filename: $filename")
        val file = File(application.filesDir, filename)
        val isCustomImported =
            if (filename == "geoip.dat") prefs.customGeoipImported else prefs.customGeositeImported
        return if (file.exists() && isCustomImported) {
            formatRuleFileSummary(file) ?: application.getString(R.string.rule_file_default)
        } else {
            application.getString(R.string.rule_file_default)
        }
    }

    /**
     * Unified summary for third-party dat files: "yyyy/MM/dd HH:mm | size".
     * Returns an empty string when the file does not exist (e.g. still downloading).
     */
    fun getCustomDatSummary(filename: String): String {
        val file = File(application.filesDir, filename)
        return if (file.exists()) {
            formatRuleFileSummary(file) ?: ""
        } else {
            ""
        }
    }

    private fun formatRuleFileSummary(file: File): String? {
        if (!file.exists()) return null
        val lastModified = file.lastModified()
        val date = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .format(Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()))
        return "$date | ${formatFileSize(file.length())}"
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            size / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
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

    suspend fun restoreDefaultGeoip(): Boolean {
        return withContext(Dispatchers.IO) {
            prefs.customGeoipImported = false
            val file = File(application.filesDir, "geoip.dat")
            application.assets.open("geoip.dat").use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        }
    }

    suspend fun restoreDefaultGeosite(): Boolean {
        return withContext(Dispatchers.IO) {
            prefs.customGeositeImported = false
            val file = File(application.filesDir, "geosite.dat")
            application.assets.open("geosite.dat").use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        }
    }

    suspend fun importDatFileFromUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = getDatFileNameFromUri(context, uri) ?: return@withContext null
                // Defense: standard GEO file names (case-insensitive) must not be
                // imported through the third-party dat path.
                if (FileManager.isStandardGeoDat(fileName)) {
                    Log.w(TAG, "Rejected import of standard GEO file via custom dat path: $fileName")
                    return@withContext null
                }
                val targetFile = File(application.filesDir, fileName)
                val tempFile = File(application.filesDir, "$fileName.tmp")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (com.simplexray.re.common.GeoDataValidator.validateDatFile(application, tempFile, fileName)) {
                    if (tempFile.renameTo(targetFile)) {
                        return@withContext fileName
                    }
                }
                tempFile.delete()
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error importing dat file from URI", e)
                null
            }
        }
    }

    /**
     * Resolve the target file name for a dat file picked from URI.
     * Returns null if the selected file does not have a .dat extension.
     * Sanitizes the display name so a malicious provider cannot inject path
     * separators or relative segments.
     */
    fun getDatFileNameFromUri(context: Context, uri: Uri): String? {
        val originalName = getFileNameFromUri(context, uri) ?: return null
        val fileName = sanitizeFileName(originalName, fallback = "")
        if (!fileName.lowercase().endsWith(".dat")) {
            return null
        }
        return fileName
    }

    /**
     * Strips path separators and relative segments from a provider-supplied
     * display name so it cannot escape the target directory.
     */
    private fun sanitizeFileName(fileName: String, fallback: String): String {
        var name = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (name.isBlank() || name == "." || name == "..") {
            name = fallback
        }
        return name
    }

    suspend fun importConfigFileFromUri(context: Context, uri: Uri): String? {        return withContext(Dispatchers.IO) {
            try {
                var fileName = sanitizeFileName(
                    getFileNameFromUri(context, uri) ?: "imported_config.json",
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

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    companion object {
        const val TAG = "FileManager"

        /**
         * True when [fileName] collides (case-insensitively) with the built-in
         * standard GEO resources, which must only be replaced via the dedicated
         * top section.
         */
        fun isStandardGeoDat(fileName: String): Boolean {
            val lower = fileName.lowercase()
            return lower == "geoip.dat" || lower == "geosite.dat"
        }
    }
}
