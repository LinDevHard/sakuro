import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Desktop/JVM UI sandbox entry point (ARCHITECTURE.md §3.2): FakePlayerEngine only.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("sakuro.detekt")
    id("sakuro.module-rules")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-player"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-upscale"))
    implementation(project(":core:core-media"))
    implementation(project(":core:core-detect"))
    implementation(project(":core:core-settings"))
    implementation(project(":engine:engine-fake"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.koin.core)
}

// macOS packaging requires MAJOR > 0, so a 0.x Android version maps to 1.x here.
val desktopPackageVersion = providers.gradleProperty("sakuro.versionName").get()
    .split('.')
    .let { (major, minor, patch) -> "${major.toInt().coerceAtLeast(1)}.$minor.$patch" }

compose.desktop {
    application {
        mainClass = "com.rinwave.sakuro.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sakuro"
            packageVersion = desktopPackageVersion
        }
    }
}
