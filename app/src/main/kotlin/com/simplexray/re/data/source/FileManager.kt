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
import com.simplexray.re.common.configFormat.ConfigFormatConverter
import com.simplexray.re.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
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
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
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
                val fileContent: String
                if (prefs.useTemplate) {
                    assets.open("template").use { assetInputStream ->
                        val size = assetInputStream.available()
                        val buffer = ByteArray(size)
                        assetInputStream.read(buffer)
                        fileContent = String(buffer, StandardCharsets.UTF_8)
                    }
                } else {
                    fileContent = "{}"
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

            val (name, configContent) = ConfigFormatConverter.convert(application, content).getOrElse { e ->
                Log.e(TAG, "Failed to parse config", e)
                return@withContext null
            }

            val formattedContent = try {
                ConfigUtils.formatConfigContent(configContent)
            } catch (e: JSONException) {
                Log.e(TAG, "Invalid JSON format in provided content.", e)
                return@withContext null
            }

            val filename = "$name.json"
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

    suspend fun compressBackupData(): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val preferencesJson = buildJsonObject {
                    put(Preferences.SOCKS_ADDR, prefs.socksAddress)
                    put(Preferences.SOCKS_PORT, prefs.socksPort)
                    put(Preferences.SOCKS_USER, prefs.socksUsername)
                    put(Preferences.SOCKS_PASS, prefs.socksPassword)
                    put(Preferences.DNS_IPV4, prefs.dnsIpv4)
                    put(Preferences.DNS_IPV6, prefs.dnsIpv6)
                    put(Preferences.IPV6, prefs.ipv6)
                    put(Preferences.APPS, buildJsonArray { (prefs.apps ?: emptySet()).filterNotNull().forEach { add(it) } })
                    put(Preferences.BYPASS_LAN, prefs.bypassLan)
                    put(Preferences.USE_TEMPLATE, prefs.useTemplate)
                    put(Preferences.HTTP_PROXY_ENABLED, prefs.httpProxyEnabled)
                    put(Preferences.CONFIG_FILES_ORDER, buildJsonArray { prefs.configFilesOrder.forEach { add(it) } })
                    put(Preferences.DISABLE_VPN, prefs.disableVpn)
                    put(Preferences.CONNECTIVITY_TEST_TARGET, prefs.connectivityTestTarget)
                    put(Preferences.CONNECTIVITY_TEST_TIMEOUT, prefs.connectivityTestTimeout)
                    put(Preferences.GEOIP_URL, prefs.geoipUrl)
                    put(Preferences.GEOSITE_URL, prefs.geositeUrl)
                    put(Preferences.BYPASS_SELECTED_APPS, prefs.bypassSelectedApps)
                }
                val configFilesJson = buildJsonObject {
                    val filesDir = application.filesDir
                    val files = filesDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile && file.isConfigFile()) {
                                try {
                                    val content = readFileContent(file)
                                    put(file.name, content)
                                } catch (e: IOException) {
                                    Log.e(TAG, "Error reading config file: ${file.name}", e)
                                }
                            }
                        }
                    }
                }
                val backupJson = buildJsonObject {
                    put("preferences", preferencesJson)
                    put("configFiles", configFilesJson)
                }
                val jsonString = Json.encodeToString(backupJson)
                val input = jsonString.toByteArray(StandardCharsets.UTF_8)
                val deflater = Deflater()
                deflater.setInput(input)
                deflater.finish()
                val outputStream = ByteArrayOutputStream(input.size)
                val buffer = ByteArray(1024)
                while (!deflater.finished()) {
                    val count = deflater.deflate(buffer)
                    outputStream.write(buffer, 0, count)
                }
                outputStream.close()
                deflater.end()
                outputStream.toByteArray()
            } catch (e: Exception) {
                Log.e(TAG, "Error during backup compression", e)
                null
            }
        }
    }

    suspend fun decompressAndRestore(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var compressedData: ByteArray
                application.contentResolver.openInputStream(uri).use { `is` ->
                    if (`is` == null) {
                        throw IOException("Failed to open input stream for URI: $uri")
                    }
                    val buffer = ByteArrayOutputStream()
                    var nRead: Int
                    val data = ByteArray(1024)
                    while ((`is`.read(data, 0, data.size).also { nRead = it }) != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    compressedData = buffer.toByteArray()
                }
                val inflater = Inflater()
                inflater.setInput(compressedData)
                val outputStream = ByteArrayOutputStream(compressedData.size)
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    try {
                        val count = inflater.inflate(buffer)
                        if (count == 0 && inflater.needsInput()) {
                            Log.e(TAG, "Incomplete compressed data during inflation.")
                            throw IOException("Incomplete compressed data.")
                        }
                        if (count > 0) {
                            outputStream.write(buffer, 0, count)
                        }
                    } catch (e: DataFormatException) {
                        Log.e(TAG, "Data format error during inflation", e)
                        throw IOException("Error decompressing data: Invalid format.", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during inflation", e)
                        throw IOException("Error decompressing data.", e)
                    }
                }
                outputStream.close()
                val decompressedData = outputStream.toByteArray()
                inflater.end()

                val jsonString = String(decompressedData, StandardCharsets.UTF_8)
                val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

                require(jsonObject.containsKey("preferences") && jsonObject.containsKey("configFiles")) {
                    "Invalid backup file format."
                }

                val preferencesMap = jsonObject["preferences"]?.jsonObject
                val configFilesMap = jsonObject["configFiles"]?.jsonObject

                val savedOrderFromBackup = mutableListOf<String>()

                if (preferencesMap != null) {
                    val portVal = preferencesMap[Preferences.SOCKS_PORT]?.jsonPrimitive
                    portVal?.intOrNull?.let { prefs.socksPort = it }
                        ?: portVal?.contentOrNull?.toIntOrNull()?.let { prefs.socksPort = it }

                    preferencesMap[Preferences.SOCKS_ADDR]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.socksAddress = it
                    }

                    preferencesMap[Preferences.SOCKS_USER]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.socksUsername = it
                    }

                    preferencesMap[Preferences.SOCKS_PASS]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.socksPassword = it
                    }

                    preferencesMap[Preferences.DNS_IPV4]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.dnsIpv4 = it
                    }

                    preferencesMap[Preferences.DNS_IPV6]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.dnsIpv6 = it
                    }

                    preferencesMap[Preferences.IPV6]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.ipv6 = it
                    }

                    preferencesMap[Preferences.BYPASS_LAN]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.bypassLan = it
                    }

                    preferencesMap[Preferences.USE_TEMPLATE]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.useTemplate = it
                    }

                    preferencesMap[Preferences.HTTP_PROXY_ENABLED]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.httpProxyEnabled = it
                    }

                    preferencesMap[Preferences.APPS]?.jsonArray?.let { array ->
                        val appsSet = array.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                        prefs.apps = appsSet
                    }

                    preferencesMap[Preferences.DISABLE_VPN]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.disableVpn = it
                    }

                    preferencesMap[Preferences.CONNECTIVITY_TEST_TARGET]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.connectivityTestTarget = it
                    }

                    val timeoutVal = preferencesMap[Preferences.CONNECTIVITY_TEST_TIMEOUT]?.jsonPrimitive
                    timeoutVal?.intOrNull?.let { prefs.connectivityTestTimeout = it }
                        ?: timeoutVal?.contentOrNull?.toIntOrNull()?.let { prefs.connectivityTestTimeout = it }

                    preferencesMap[Preferences.GEOIP_URL]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.geoipUrl = it
                    }

                    preferencesMap[Preferences.GEOSITE_URL]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.geositeUrl = it
                    }

                    preferencesMap[Preferences.BYPASS_SELECTED_APPS]?.jsonPrimitive?.booleanOrNull?.let {
                        prefs.bypassSelectedApps = it
                    }

                    preferencesMap[Preferences.CONFIG_FILES_ORDER]?.jsonArray?.let { array ->
                        array.mapNotNull { it.jsonPrimitive.contentOrNull }.forEach {
                            savedOrderFromBackup.add(it)
                        }
                    }
                } else {
                    Log.w(TAG, "Preferences map is null.")
                }

                val filesDir = application.filesDir

                if (configFilesMap != null) {
                    for ((filename, jsonElement) in configFilesMap) {
                        val content = jsonElement.jsonPrimitive.contentOrNull ?: continue
                        if (FilenameValidator.validateFilename(application, filename) != null) {
                            Log.e(TAG, "Skipping restore of invalid filename: $filename")
                            continue
                        }
                        val configFile = File(filesDir, filename)
                        try {
                            FileOutputStream(configFile).use { fos ->
                                fos.write(content.toByteArray(StandardCharsets.UTF_8))
                                Log.d(TAG, "Successfully restored/overwrote config file: $filename")
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing config file: $filename", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Config files map is null.")
                }

                val existingFileNames = prefs.configFilesOrder.toMutableList()
                val actualFileNamesAfterRestore =
                    filesDir.listFiles { file -> file.isFile && file.isConfigFile() }
                        ?.map { it.name }?.toMutableSet() ?: mutableSetOf()

                val finalConfigOrder = mutableListOf<String>()
                val processedFileNames = mutableSetOf<String>()

                savedOrderFromBackup.forEach { filename ->
                    if (actualFileNamesAfterRestore.contains(filename)) {
                        finalConfigOrder.add(filename)
                        processedFileNames.add(filename)
                    }
                }

                existingFileNames.forEach { filename ->
                    if (actualFileNamesAfterRestore.contains(filename) && !processedFileNames.contains(
                            filename
                        )
                    ) {
                        finalConfigOrder.add(filename)
                        processedFileNames.add(filename)
                    }
                }

                val newlyAddedFileNames =
                    actualFileNamesAfterRestore.filter { !processedFileNames.contains(it) }.sorted()
                finalConfigOrder.addAll(newlyAddedFileNames)

                prefs.configFilesOrder = finalConfigOrder

                Log.d(TAG, "Restore successful.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error during restore process", e)
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
            try {
                application.contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        if (inputStream == null) {
                            throw IOException("Failed to open input stream for URI: $uri")
                        }
                        val buffer = ByteArray(1024)
                        var read: Int
                        while ((inputStream.read(buffer).also { read = it }) != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        when (filename) {
                            "geoip.dat" -> prefs.customGeoipImported = true
                            "geosite.dat" -> prefs.customGeositeImported = true
                        }
                        Log.d(TAG, "Successfully imported $filename from URI: $uri")
                        true
                    }
                }
            } catch (e: IOException) {
                if (filename == "geoip.dat") {
                    prefs.customGeoipImported = false
                } else if (filename == "geosite.dat") {
                    prefs.customGeositeImported = false
                }
                Log.e(TAG, "Error importing rule file: $filename", e)
                false
            } catch (e: Exception) {
                if (filename == "geoip.dat") {
                    prefs.customGeoipImported = false
                } else if (filename == "geosite.dat") {
                    prefs.customGeositeImported = false
                }
                Log.e(TAG, "Unexpected error during rule file import: $filename", e)
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

                if (tempFile.renameTo(targetFile)) {
                    when (filename) {
                        "geoip.dat" -> prefs.customGeoipImported = true
                        "geosite.dat" -> prefs.customGeositeImported = true
                    }
                    Log.d(TAG, "Successfully saved $filename from stream")
                    true
                } else {
                    Log.e(TAG, "Failed to rename temp file to $filename")
                    tempFile.delete()
                    false
                }
            } catch (e: IOException) {
                tempFile.delete()
                Log.e(TAG, "Error saving rule file: $filename", e)
                false
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "Unexpected error during rule file save: $filename", e)
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
            val lastModified = file.lastModified()
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            val date = sdf.format(Date(lastModified))
            val size = formatFileSize(file.length())
            "$date | $size"
        } else {
            application.getString(R.string.rule_file_default)
        }
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
                var fileName = getFileNameFromUri(context, uri) ?: "custom.dat"
                if (!fileName.lowercase().endsWith(".dat")) {
                    fileName += ".dat"
                }
                val targetFile = File(application.filesDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                fileName
            } catch (e: Exception) {
                Log.e(TAG, "Error importing dat file from URI", e)
                null
            }
        }
    }

    suspend fun importConfigFileFromUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                var fileName = getFileNameFromUri(context, uri) ?: "imported_config.json"
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
    }
}
