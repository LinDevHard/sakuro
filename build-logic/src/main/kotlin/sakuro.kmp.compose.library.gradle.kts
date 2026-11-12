// KMP library with Compose Multiplatform UI and its own Compose Resources.
// The generated Res class lives in com.rinwave.sakuro.<group>.<name>.resources,
// derived from the project path (":core:core-ui" -> core.ui).
plugins {
    id("sakuro.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
    }
}

kotlin {
    android {
        // Compose Resources are packaged as Android assets/resources of the library.
        androidResources {
            enable = true
        }
    }
}

compose.resources {
    packageOfResClass = "com.rinwave.sakuro." + sakuroModuleSuffix(path) + ".resources"
}
