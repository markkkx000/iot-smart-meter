plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}

// Add the dependency for the Hilt Gradle plugin in your project-level build.gradle.kts
// Alternatively, you can add it to settings.gradle.kts if you prefer
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.hilt.android.gradle.plugin)
    }
}

// Ensure you have the Hilt plugin defined in libs.versions.toml if you're using version catalogs.
// For example:
// [plugins]
// hilt = {
//     id = "com.google.dagger.hilt.android",
//     version = "2.50" // Use the latest Hilt version
// }
