import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.versions)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    checkForGradleUpdate = false
    checkConstraints = false
    rejectVersionIf {
        val version = candidate.version
        version.contains("alpha") || version.contains("beta") || version.contains("RC")
    }
    filterConfigurations = org.gradle.api.specs.Spec { config ->
        config.name in listOf("implementation", "api", "runtimeOnly")
    }
}
