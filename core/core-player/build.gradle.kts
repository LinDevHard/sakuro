plugins {
    id("sakuro.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-upscale"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.core.player"
}
