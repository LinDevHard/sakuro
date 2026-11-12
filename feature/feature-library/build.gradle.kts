// Library feature: the local video catalog (videos/folders, sorting, thumbnails).
plugins {
    id("sakuro.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core-media"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.video)
        }
    }
}

android {
    namespace = "com.rinwave.sakuro.feature.library"
}
