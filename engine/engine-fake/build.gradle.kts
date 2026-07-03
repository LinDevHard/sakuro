plugins {
    id("sakuro.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-player"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.engine.fake"
}
