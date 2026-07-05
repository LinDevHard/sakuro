import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android-only Sakuro library (platform-bound engines): JVM 17, detekt.
// The module only has to declare its namespace in android {} and its dependencies.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("sakuro.detekt")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

android {
    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
