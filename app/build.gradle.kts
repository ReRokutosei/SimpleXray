import java.util.Properties
import java.net.URI
import java.net.HttpURLConnection
import java.net.URLConnection
import java.security.MessageDigest
import java.util.zip.ZipFile
import com.google.protobuf.gradle.proto

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.grpc.gen.java.get().toString()
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {}
            }
            task.plugins {
                create("grpc") {}
            }
        }
    }
}

fun computeVersionCode(versionName: String): Int {
    val clean = versionName.trim().removePrefix("v").removePrefix("V")
    val withoutBuild = clean.substringBefore('+')
    val core = withoutBuild.substringBefore('-').split('.')
    val major = core.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = core.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = core.getOrNull(2)?.toIntOrNull() ?: 0
    val prerelease = withoutBuild.substringAfter('-', "").ifBlank { null }

    // Keep versionCode strictly increasing across prerelease channels and the
    // final stable release. The previous core-only layout gave alpha.N,
    // beta.N, rc.N, and stable the same versionCode.
    val base = major.toLong() * 10_000_000L + minor.toLong() * 100_000L + patch.toLong() * 1_000L
    val stage = if (prerelease == null) {
        999L
    } else {
        val match = Regex("""(?i)(alpha|beta|rc)[.-]?(\d+)?""").find(prerelease)
        val channel = when (match?.groupValues?.get(1)?.lowercase()) {
            "alpha" -> 1L
            "beta" -> 2L
            "rc" -> 3L
            else -> 0L
        }
        val sequence = match?.groupValues?.get(2)?.toLongOrNull() ?: 0L
        channel * 100L + sequence.coerceIn(0L, 99L)
    }
    return (base + stage).coerceIn(1L, 2_100_000_000L).toInt()
}

val versionProps = Properties().apply {
    file("$rootDir/version.properties").inputStream().use { load(it) }
}

val appVersionName = if (project.hasProperty("appVerName")) {
    project.property("appVerName").toString().trim().removePrefix("v").removePrefix("V")
} else {
    "1.0.0"
}

val appVersionCode = if (project.hasProperty("appVerCode")) {
    project.property("appVerCode").toString().toInt()
} else {
    computeVersionCode(appVersionName)
}

val noGeoAssets = providers.gradleProperty("noGeo").map(String::toBoolean).orElse(false)

android {
    namespace = "com.simplexray.re"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.simplexray.re"
        versionCode = appVersionCode
        versionName = appVersionName
        targetSdk = 36
        minSdk = 34

        externalNativeBuild {
            cmake {
                abiFilters += "arm64-v8a"
            }
        }
        ndk {
            abiFilters += "arm64-v8a"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("store.properties")
            val props = Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }

            val ksPath = System.getenv("KEYSTORE_PATH") ?: props.getProperty("storeFile")
            if (!ksPath.isNullOrEmpty()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: props.getProperty("storePassword") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: props.getProperty("keyAlias") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: props.getProperty("keyPassword") ?: ""
            }

            enableV1Signing = false
            enableV2Signing = false
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Never ship the removed mipstack experiment even if a stale
            // local artifact is left in the ignored jniLibs directory.
            excludes += "**/libmipstun.so"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    ndkVersion = versionProps.getProperty("NDK_VERSION")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    androidComponents {
        onVariants(selector().all()) { variant ->
            variant.outputs.forEach { output ->
                (output as com.android.build.api.variant.VariantOutput).outputFileName.set("simplexray-arm64-v8a.apk")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    sourceSets {
        getByName("main") {
            java {
                directories.add("src/main/java")
                directories.add("build/generated/source/proto/main/java")
            }
            aidl {
                directories.add("src/main/aidl")
            }
            kotlin {
                directories.add("src/main/kotlin")
                directories.add("build/generated/source/proto/main/grpckt")
            }
            assets {
                directories.clear()
                directories.add(
                    if (noGeoAssets.get()) "src/main/assets-no-geo"
                    else "src/main/assets"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.java)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigationevent)

    implementation(libs.lazycolumnscrollbar)
    implementation(libs.reorderable)
    implementation(libs.okhttp)
    implementation(libs.yaml)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

val xrayVersion = versionProps.getProperty("XRAY_CORE_VERSION")?.trim() ?: "v26.9.30"
val xrayZipSha256 = versionProps.getProperty("XRAY_CORE_ZIP_SHA256")?.trim()?.lowercase()
    ?: error("XRAY_CORE_ZIP_SHA256 is missing from version.properties")
require(xrayZipSha256.matches(Regex("^[0-9a-f]{64}$"))) {
    "XRAY_CORE_ZIP_SHA256 must be a 64-character lowercase hex SHA-256"
}
val targetJniFile = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libxray.so")
val cachedLibXray = layout.buildDirectory.file("xray-core-cache/$xrayVersion-${xrayZipSha256.take(12)}/libxray.so")

val ensureXrayCore = tasks.register("ensureXrayCore") {
    group = "build"
    description = "Ensures the verified Xray prebuilt matches version.properties"

    val version = xrayVersion
    val expectedZipSha = xrayZipSha256
    val cachedFile = cachedLibXray.get().asFile
    val targetFile = targetJniFile.asFile

    inputs.property("xrayVersion", version)
    inputs.property("xrayZipSha256", expectedZipSha)
    outputs.file(cachedFile)
    outputs.file(targetFile)

    doLast {
        if (cachedFile.exists()) {
            if (!targetFile.exists() || targetFile.length() != cachedFile.length()) {
                targetFile.parentFile?.mkdirs()
                cachedFile.copyTo(targetFile, overwrite = true)
                targetFile.setExecutable(true)
            }
            return@doLast
        }

        println("Downloading Xray-core $version for arm64-v8a...")
        val downloadUrl = "https://github.com/XTLS/Xray-core/releases/download/$version/Xray-android-arm64-v8a.zip"
        val zipFile = File(cachedFile.parentFile, "xray-$version.zip")
        cachedFile.parentFile?.mkdirs()

        try {
            val connection = URI(downloadUrl).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 60000

            var currentConn: URLConnection = connection
            var redirects = 0
            while (redirects < 5) {
                if (currentConn is HttpURLConnection) {
                    val code = currentConn.responseCode
                    if (code in 301..308) {
                        val location = currentConn.getHeaderField("Location") ?: break
                        currentConn.disconnect()
                        currentConn = URI(location).toURL().openConnection()
                        redirects++
                        continue
                    }
                }
                break
            }

            currentConn.getInputStream().use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            zipFile.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actualZipSha = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            if (!actualZipSha.equals(expectedZipSha, ignoreCase = true)) {
                throw GradleException(
                    "Xray archive SHA-256 mismatch for $version: expected $expectedZipSha, got $actualZipSha"
                )
            }

            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry("xray") ?: error("Entry 'xray' not found in downloaded archive")
                zip.getInputStream(entry).use { entryIn ->
                    cachedFile.outputStream().use { fileOut ->
                        entryIn.copyTo(fileOut)
                    }
                }
            }
            zipFile.delete()
            cachedFile.setExecutable(true)

            targetFile.parentFile?.mkdirs()
            cachedFile.copyTo(targetFile, overwrite = true)
            targetFile.setExecutable(true)
            println("Successfully verified and installed libxray.so ($version)")
        } catch (e: Exception) {
            zipFile.delete()
            cachedFile.delete()
            throw GradleException("Failed to download or extract Xray-core $version: ${e.message}", e)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(ensureXrayCore)
}

