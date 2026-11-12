import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// KMP Sakuro library: Android (AGP's KMP library plugin) + jvm("desktop"), JVM 17, detekt.
// The Android namespace is derived from the project path:
// ":core:core-player" -> com.rinwave.sakuro.core.player, ":shared" -> com.rinwave.sakuro.shared.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("sakuro.detekt")
    id("sakuro.module-rules")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        namespace = "com.rinwave.sakuro." + sakuroModuleSuffix(path)
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // Runs commonTest on the JVM for Android too (task testAndroidHostTest).
        withHostTest {}
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    applyDefaultHierarchyTemplate()

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}
