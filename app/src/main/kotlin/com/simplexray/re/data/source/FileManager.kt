package com.simplexray.re.data.source

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.simplexray.re.prefs.Preferences
import java.io.File
import java.io.InputStream

class FileManager(application: Application, prefs: Preferences) {
    private val configFileStore = ConfigFileStore(application, prefs)
    private val ruleFileStore = RuleFileStore(application, prefs)

    suspend fun createConfigFile(assets: AssetManager): String? =
        configFileStore.createConfigFile(assets)

    suspend fun importConfigFromClipboard(): String? =
        configFileStore.importConfigFromClipboard()

    suspend fun importConfigFromContent(content: String): String? =
        configFileStore.importConfigFromContent(content)

    suspend fun deleteConfigFile(fileToDelete: File): Boolean =
        configFileStore.deleteConfigFile(fileToDelete)

    suspend fun renameConfigFile(oldFile: File, newFile: File, newContent: String): Boolean =
        configFileStore.renameConfigFile(oldFile, newFile, newContent)

    suspend fun importConfigFileFromUri(context: Context, uri: Uri): String? =
        configFileStore.importConfigFileFromUri(context, uri)

    fun extractAssetsIfNeeded() =
        ruleFileStore.extractAssetsIfNeeded()

    suspend fun importRuleFile(uri: Uri, filename: String): Boolean =
        ruleFileStore.importRuleFile(uri, filename)

    suspend fun saveRuleFile(
        inputStream: InputStream,
        filename: String,
        onProgress: (Int) -> Unit
    ): Boolean =
        ruleFileStore.saveRuleFile(inputStream, filename, onProgress)

    suspend fun saveRuleFileFromTemp(tempFile: File, filename: String): Boolean =
        ruleFileStore.saveRuleFileFromTemp(tempFile, filename)

    fun getRuleFileSummary(filename: String): String =
        ruleFileStore.getRuleFileSummary(filename)

    fun getCustomDatSummary(filename: String): String =
        ruleFileStore.getCustomDatSummary(filename)

    suspend fun restoreDefaultGeoip(): Boolean =
        ruleFileStore.restoreDefaultGeoip()

    suspend fun restoreDefaultGeosite(): Boolean =
        ruleFileStore.restoreDefaultGeosite()

    suspend fun importDatFileFromUri(context: Context, uri: Uri): String? =
        ruleFileStore.importDatFileFromUri(context, uri)

    fun getDatFileNameFromUri(context: Context, uri: Uri): String? =
        FileUriHelper.getDatFileNameFromUri(context, uri)

    companion object {
        fun isStandardGeoDat(fileName: String): Boolean =
            FileUriHelper.isStandardGeoDat(fileName)
    }
}
