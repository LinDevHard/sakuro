// On-device benchmark: plays a video through engine x preset configurations,
// measures playback and exports a report bundle for tools/sakuro-bench.
plugins {
    id("sakuro.feature")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-player"))
            implementation(project(":core:core-upscale"))
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}
