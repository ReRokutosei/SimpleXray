import java.util.Properties
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
    val parts = versionName.split("-")[0].split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}

val versionProps = Properties().apply {
    file("$rootDir/version.properties").inputStream().use { load(it) }
}

val appVersionName = if (project.hasProperty("appVerName")) {
    project.property("appVerName").toString()
} else {
    "1.0.0"
}

val appVersionCode = if (project.hasProperty("appVerCode")) {
    project.property("appVerCode").toString().toInt()
} else {
    computeVersionCode(appVersionName)
}

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
    }

    sourceSets {
        getByName("main") {
            java {
                srcDirs("src/main/java", "build/generated/source/proto/main/java")
            }
            kotlin {
                srcDirs("src/main/kotlin", "build/generated/source/proto/main/grpckt")
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
