// Settings feature: playback, upscale presets and shaders, controls, advanced, about.
plugins {
    id("sakuro.feature")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-player"))
            implementation(project(":core:core-settings"))
            implementation(project(":core:core-upscale"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
