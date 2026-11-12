import org.gradle.api.artifacts.VersionCatalogsExtension

// A self-contained feature: Decompose component + Compose screen.
// Explicit API mode keeps the public surface to the component, its factory,
// its outputs, its screen and its Koin module; everything else is internal.
plugins {
    id("sakuro.kmp.compose.library")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-designsystem"))
            implementation(project(":core:core-ui"))
            implementation(project(":core:core-model"))
            implementation(libs.findLibrary("decompose").get())
            implementation(libs.findLibrary("decompose-extensions-compose").get())
            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("lucide-icons").get())
            implementation(libs.findLibrary("kotlinx-coroutines-core").get())
        }
    }
}
