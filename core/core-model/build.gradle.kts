// Shared domain types with no behavior beyond validation: the bottom of the
// flat module graph. Every other core module may depend on this one only.
plugins {
    id("sakuro.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
