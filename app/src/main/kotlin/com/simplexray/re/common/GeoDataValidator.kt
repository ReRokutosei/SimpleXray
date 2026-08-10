package com.simplexray.re.common

import android.content.Context
import android.util.Log
import com.simplexray.re.service.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object GeoDataValidator {
    private const val TAG = "GeoDataValidator"

    /**
     * Validate candidate dat file before saving or overwriting.
     * 1. Basic sanity check (file size >= 1KB, non-HTML/HTTP error header).
     * 2. Spawn a temporary shadow sandbox child process of `libxray.so` to verify parsing.
     */
    fun validateDatFile(context: Context, candidateFile: File, targetFileName: String): Boolean {
        // Step 1: Basic sanity check
        if (!candidateFile.exists() || candidateFile.length() < 1024) {
            Log.e(TAG, "Validation failed for $targetFileName: file does not exist or is too small (${candidateFile.length()} bytes)")
            return false
        }

        try {
            val headerBytes = ByteArray(512)
            candidateFile.inputStream().use { it.read(headerBytes) }
            val headerStr = String(headerBytes, Charsets.UTF_8).lowercase()
            if (headerStr.contains("<html") || headerStr.contains("<!doctype") ||
                headerStr.contains("404: not found") || headerStr.contains("404 not found") ||
                headerStr.contains("403 forbidden") || headerStr.contains("500 internal server error")
            ) {
                Log.e(TAG, "Validation failed for $targetFileName: file content is HTML or HTTP error response")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file header for $targetFileName", e)
            return false
        }

        // Step 2: Xray-core Child Process Shadow Sandbox Verification
        val sandboxDir = File(context.cacheDir, "geodata_sandbox_${System.currentTimeMillis()}")
        if (!sandboxDir.mkdirs()) {
            Log.e(TAG, "Failed to create sandbox directory: ${sandboxDir.absolutePath}")
            return false
        }

        val testConfigFile = File(sandboxDir, "test_config.json")

        try {
            // Copy candidate file to sandbox directory
            val sandboxDatFile = File(sandboxDir, targetFileName)
            candidateFile.copyTo(sandboxDatFile, overwrite = true)

            // Copy existing valid geoip.dat & geosite.dat from filesDir if available
            val filesDir = context.filesDir
            listOf("geoip.dat", "geosite.dat").forEach { defaultDat ->
                if (defaultDat != targetFileName) {
                    val existing = File(filesDir, defaultDat)
                    if (existing.exists()) {
                        existing.copyTo(File(sandboxDir, defaultDat), overwrite = true)
                    }
                }
            }

            val ruleType = if (targetFileName == "geoip.dat") "geoip" else "geosite"
            val isCustom = targetFileName != "geoip.dat" && targetFileName != "geosite.dat"
            val tagRef = if (isCustom) "ext:$targetFileName:cn" else "cn"

            val testConfigJson = """
                {
                  "log": { "loglevel": "warning" },
                  "inbounds": [],
                  "outbounds": [{ "protocol": "freedom", "tag": "direct" }],
                  "routing": {
                    "rules": [
                      { "type": "field", "outboundTag": "direct", "$ruleType": ["$tagRef"] }
                    ]
                  }
                }
            """.trimIndent()

            FileOutputStream(testConfigFile).use { it.write(testConfigJson.toByteArray(Charsets.UTF_8)) }

            val libraryDir = TProxyService.getNativeLibraryDir(context)
            val xrayPath = "$libraryDir/libxray.so"

            val processBuilder = ProcessBuilder(xrayPath, "run", "-test", "-config", testConfigFile.absolutePath)
            processBuilder.directory(sandboxDir)
            processBuilder.environment()["XRAY_LOCATION_ASSET"] = sandboxDir.absolutePath
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.e(TAG, "Validation failed: Xray sandbox process timed out for $targetFileName.")
                return false
            }

            val exitCode = process.exitValue()
            if (exitCode == 0) {
                Log.d(TAG, "Validation SUCCESS: Xray sandbox verified $targetFileName successfully.")
                return true
            } else {
                val errorLog = process.inputStream.bufferedReader().readText()
                Log.e(TAG, "Validation FAILED: Xray sandbox exitCode=$exitCode for $targetFileName. Output:\n$errorLog")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Xray sandbox validation for $targetFileName", e)
            return false
        } finally {
            sandboxDir.deleteRecursively()
        }
    }
}
