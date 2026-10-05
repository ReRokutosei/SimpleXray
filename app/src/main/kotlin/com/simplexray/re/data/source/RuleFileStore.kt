package com.simplexray.re.data.source

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import com.simplexray.re.R
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "RuleFileStore"

internal class RuleFileStore(
    private val application: Application,
    private val prefs: Preferences,
) {
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

    fun extractAssetsIfNeeded() {
        val files = arrayOf("geoip.dat", "geosite.dat")
        val dir = application.filesDir
        dir.mkdirs()
        for (file in files) {
            val targetFile = File(dir, file)
            val isCustomImported =
                if (file == "geoip.dat") prefs.customGeoipImported else prefs.customGeositeImported

            if (isCustomImported) {
                Log.d(TAG, "Custom file already imported for $file, skipping asset extraction.")
                continue
            }

            if (!hasBundledAsset(file)) {
                // NoGEO builds intentionally ship without geo assets. Missing
                // GEO data is only an error when the selected config references
                // it; the app itself must stay alive and let the GUI surface the
                // missing rule-file state for import/download.
                Log.i(TAG, "Bundled asset $file is not packaged; skipping extraction.")
                continue
            }

            var needsExtraction = false
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
                if (copyAssetToFile(file, targetFile)) {
                    Log.d(TAG, "Extracted asset $file to ${targetFile.absolutePath}")
                } else {
                    Log.e(TAG, "Failed to extract asset: $file")
                }
            } else {
                Log.d(TAG, "Asset $file already exists and matches hash, skipping extraction.")
            }
        }
    }

    private fun hasBundledAsset(name: String): Boolean {
        return runCatching { application.assets.open(name).use { } }.isSuccess
    }

    /**
     * Copies a bundled asset to [targetFile] through a temporary file so an
     * interrupted extraction can never leave a truncated .dat in place.
     */
    private fun copyAssetToFile(assetName: String, targetFile: File): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        return try {
            application.assets.open(assetName).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (tempFile.renameTo(targetFile)) {
                true
            } else {
                tempFile.delete()
                Log.e(TAG, "Failed to rename ${tempFile.name} to ${targetFile.name}")
                false
            }
        } catch (e: IOException) {
            tempFile.delete()
            Log.e(TAG, "Failed to copy bundled asset $assetName", e)
            false
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
        return when {
            !file.exists() -> application.getString(R.string.rule_file_missing)
            isCustomImported ->
                formatRuleFileSummary(file) ?: application.getString(R.string.rule_file_default)
            else -> application.getString(R.string.rule_file_default)
        }
    }

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

    suspend fun restoreDefaultGeoip(): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(application.filesDir, "geoip.dat")
            if (!copyAssetToFile("geoip.dat", file)) {
                Log.w(TAG, "No bundled geoip.dat available to restore.")
                false
            } else {
                prefs.customGeoipImported = false
                true
            }
        }
    }

    suspend fun restoreDefaultGeosite(): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(application.filesDir, "geosite.dat")
            if (!copyAssetToFile("geosite.dat", file)) {
                Log.w(TAG, "No bundled geosite.dat available to restore.")
                false
            } else {
                prefs.customGeositeImported = false
                true
            }
        }
    }

    suspend fun importDatFileFromUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = FileUriHelper.getDatFileNameFromUri(context, uri) ?: return@withContext null
                // Defense: standard GEO file names (case-insensitive) must not be
                // imported through the third-party dat path.
                if (FileUriHelper.isStandardGeoDat(fileName)) {
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
}
