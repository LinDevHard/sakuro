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

// Android entry point: Activity, Application, manifest, flavors, signing and R8.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("sakuro.detekt")
    id("sakuro.module-rules")
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

    // Two flavors (docs/architecture.md §6): foss — F-Droid, full — reference/benchmark.
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

    // F-Droid rejects AGP's optional dependency metadata signing block, and it
    // is not required for app execution or reproducible verification.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Also analyze the shared/engine/core/lib modules the app links, in one report.
        checkDependencies = true
        abortOnError = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
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
    implementation(project(":engine:engine-media3"))
    implementation(project(":engine:engine-fake"))
    // The F-Droid runtime never resolves the prebuilt libmpv AAR.
    "fullImplementation"(project(":engine:engine-mpv"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.ui)
    implementation(libs.decompose)
    implementation(libs.koin.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}

// AGP's compiled baseline profiles are nondeterministic across otherwise
// identical release builds. F-Droid verifies upstream signatures byte-for-byte,
// so release APKs must omit these generated binary profiles.
tasks.configureEach {
    if (name.contains("ReleaseArtProfile")) {
        enabled = false
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
