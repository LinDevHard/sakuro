plugins {
    id("sakuro.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.core.upscale"
}
