// Shared presentation helpers over domain types: localized labels, the engine
// video surface contract, file pickers, diagnostics sharing and Decompose helpers.
plugins {
    id("sakuro.kmp.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-model"))
            api(project(":core:core-player"))
            implementation(project(":core:core-upscale"))
            implementation(project(":core:core-designsystem"))
            api(libs.decompose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
