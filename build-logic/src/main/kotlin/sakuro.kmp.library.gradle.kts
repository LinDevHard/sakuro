import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// KMP-библиотека Sakuro: androidTarget + jvm("desktop"), JVM 17, детект.
// Модулю остаётся объявить namespace в android {} и свои зависимости.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("sakuro.detekt")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop")
    applyDefaultHierarchyTemplate()

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

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
