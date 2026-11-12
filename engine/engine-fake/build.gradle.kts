plugins {
    id("sakuro.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-player"))
            implementation(project(":core:core-upscale"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
