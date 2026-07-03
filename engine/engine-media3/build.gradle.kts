plugins {
    id("sakuro.android.library")
}

android {
    namespace = "com.rinwave.sakuro.engine.media3"
}

dependencies {
    api(project(":core:core-player"))
    implementation(libs.kotlinx.coroutines.android)
    api(libs.media3.exoplayer)
    implementation(libs.media3.effect)
    api(libs.media3.common)
}
