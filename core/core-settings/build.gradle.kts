plugins {
    id("sakuro.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-player"))
            api(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.core.settings"
}
