import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseVersionName = providers.gradleProperty("sakuro.versionName").get()
val releaseVersionCode = providers.gradleProperty("sakuro.versionCode").get().toInt()
val enableR8 = providers.gradleProperty("sakuro.enableR8").get().toBooleanStrict()
val enableReleaseSigning = providers.gradleProperty("sakuro.signing.enabled")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

fun signingSecret(name: String): String = providers.environmentVariable(name).orNull
    ?: error("$name is required when -Psakuro.signing.enabled=true")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("sakuro.detekt")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop")
    applyDefaultHierarchyTemplate()

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(project(":core:core-player"))
            implementation(project(":core:core-upscale"))
            implementation(project(":core:core-media"))
            implementation(project(":core:core-detect"))
            implementation(project(":core:core-settings"))
            implementation(project(":engine:engine-fake"))

            implementation(libs.decompose)
            implementation(libs.decompose.extensions.compose)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.lucide.icons)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(project(":engine:engine-media3"))
            implementation(libs.media3.ui)
            // Preview thumbnails in the library (ARCHITECTURE.md: Coil 3).
            // androidMain only: desktop has no video decoder, so a placeholder is used there.
            implementation(libs.coil.compose)
            implementation(libs.coil.video)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rinwave.sakuro"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    // Two flavors (ARCHITECTURE.md §6): foss — F-Droid, full — reference/benchmark.
    // foss intentionally excludes the prebuilt libmpv AAR and all mpv-specific code.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
        }
        create("full") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        if (enableReleaseSigning) {
            create("release") {
                storeFile = file(signingSecret("SAKURO_UPLOAD_KEYSTORE_FILE"))
                storePassword = signingSecret("SAKURO_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = signingSecret("SAKURO_UPLOAD_KEY_ALIAS")
                keyPassword = signingSecret("SAKURO_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = enableR8
            isShrinkResources = enableR8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (enableReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.register("printReleaseInfo") {
    group = "release"
    description = "Prints non-secret release identity and build switches."
    doLast {
        println("versionName=$releaseVersionName")
        println("versionCode=$releaseVersionCode")
        println("r8=$enableR8")
        println("signing=$enableReleaseSigning")
    }
}

afterEvaluate {
    // KMP creates this Android flavor bucket after the Android variants exist.
    // The F-Droid runtime therefore never resolves the prebuilt libmpv AAR.
    dependencies.add("androidFullImplementation", project(":engine:engine-mpv"))
}

compose.desktop {
    application {
        mainClass = "com.rinwave.sakuro.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sakuro"
            // macOS packaging requires MAJOR > 0; Android uses the canonical 0.1.0.
            packageVersion = "1.0.0"
        }
    }
}
