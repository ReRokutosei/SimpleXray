package com.simplexray.re.data.source

import android.content.Context
import android.net.Uri

internal object FileUriHelper {
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
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

    fun sanitizeFileName(fileName: String, fallback: String): String {
        var name = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (name.isBlank() || name == "." || name == "..") {
            name = fallback
        }
        return name
    }

    fun getDatFileNameFromUri(context: Context, uri: Uri): String? {
        val originalName = getFileNameFromUri(context, uri) ?: return null
        val fileName = sanitizeFileName(originalName, fallback = "")
        if (!fileName.lowercase().endsWith(".dat")) {
            return null
        }
        return fileName
    }

    fun isStandardGeoDat(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower == "geoip.dat" || lower == "geosite.dat"
    }
}
