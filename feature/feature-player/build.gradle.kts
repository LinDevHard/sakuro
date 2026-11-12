// Player feature: playback screen, transport, gestures, PiP/immersive glue,
// adaptive upscale control and the playback session (resume, errors, diagnostics).
plugins {
    id("sakuro.feature")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-player"))
            implementation(project(":core:core-upscale"))
            implementation(project(":core:core-settings"))
            implementation(project(":core:core-media"))
            implementation(project(":core:core-detect"))
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.feature.player"
}
