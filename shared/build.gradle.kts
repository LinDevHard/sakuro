// Shared Compose UI and the composition root (RootComponent, DI assembly, App()).
// Entry points, flavors and packaging live in :androidApp and :desktopApp.
plugins {
    id("sakuro.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(project(":core:core-designsystem"))
            implementation(project(":core:core-model"))
            implementation(project(":core:core-player"))
            implementation(project(":core:core-ui"))
            implementation(project(":core:core-upscale"))
            implementation(project(":core:core-media"))
            implementation(project(":core:core-detect"))
            implementation(project(":core:core-settings"))
            implementation(project(":feature:feature-bench"))
            implementation(project(":feature:feature-library"))
            implementation(project(":feature:feature-player"))
            implementation(project(":feature:feature-settings"))

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
            // Preview thumbnails in the library (ARCHITECTURE.md: Coil 3).
            // androidMain only: desktop has no video decoder, so a placeholder is used there.
            implementation(libs.coil.compose)
            implementation(libs.coil.video)
        }

        val desktopTest by getting
        desktopTest.dependencies {
            implementation(project(":core:core-settings"))
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.common)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.shared"
}
