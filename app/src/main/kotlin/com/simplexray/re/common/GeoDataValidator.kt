package com.simplexray.re.common

import android.content.Context
import android.util.Log
import java.io.File

object GeoDataValidator {
    private const val TAG = "GeoDataValidator"

    /**
     * Validate a candidate dat file before saving or overwriting.
     *
     * Performs a lightweight sanity check only:
     * 1. File must exist and be at least 1 KB.
     * 2. File header must not be an HTML page or HTTP error response
     *    (i.e., the file is not a download-error page served by a CDN/mirror).
     *
     * Why no xray shadow-sandbox test?
     * - Standard geo files (geoip.dat ~17 MB, geosite.dat ~4 MB) require copying the
     *   companion file into a temp dir, then spawning libxray.so to parse both.
     *   That consistently exceeds a 5-second timeout on real devices.
     * - Third-party dat files have unknown tag names, so any tag reference in the
     *   test config would cause xray to exit with a non-zero code regardless of
     *   file validity.
     * - A genuinely corrupt dat file will cause xray to fail on startup, which the
     *   user will see immediately in the logs. The sanity check prevents the most
     *   common failure mode: a CDN returning an HTML 404/403 page instead of the
     *   actual binary file.
     */
    fun validateDatFile(context: Context, candidateFile: File, targetFileName: String): Boolean {
        // Check 1: file must exist and be at least 1 KB
        if (!candidateFile.exists() || candidateFile.length() < 1024) {
            Log.e(
                TAG,
                "Validation failed for $targetFileName: file does not exist or is too small " +
                    "(${candidateFile.length()} bytes)"
            )
            return false
        }

        // Check 2: header must not be an HTML page or HTTP error body
        return try {
            val headerBytes = ByteArray(512)
            val read = candidateFile.inputStream().use { it.read(headerBytes) }
            if (read <= 0) {
                Log.e(TAG, "Validation failed for $targetFileName: could not read file header")
                return false
            }
            val headerStr = String(headerBytes, 0, read, Charsets.UTF_8).lowercase()
            val isHtmlOrError = headerStr.contains("<html") ||
                headerStr.contains("<!doctype") ||
                headerStr.contains("404: not found") ||
                headerStr.contains("404 not found") ||
                headerStr.contains("403 forbidden") ||
                headerStr.contains("500 internal server error")

            if (isHtmlOrError) {
                Log.e(TAG, "Validation failed for $targetFileName: header looks like an HTML/HTTP error page")
                false
            } else {
                Log.d(TAG, "Validation passed for $targetFileName (size=${candidateFile.length()} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file header for $targetFileName", e)
            false
        }
    }
}
