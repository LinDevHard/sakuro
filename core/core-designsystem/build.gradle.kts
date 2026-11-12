// Sakuro's visual language: theme, typography (Inter), shapes and generic
// components. Knows nothing about the domain.
plugins {
    id("sakuro.kmp.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lucide.icons)
        }
    }
}
