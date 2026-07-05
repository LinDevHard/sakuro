plugins {
    id("sakuro.android.library")
}

android {
    namespace = "com.rinwave.sakuro.engine.mpv"
}

dependencies {
    api(project(":core:core-player"))
    implementation(libs.kotlinx.coroutines.android)
    // Parsing the mpv `track-list` property (JSON) without @Serializable classes.
    implementation(libs.kotlinx.serialization.json)
    api(libs.jdtech.libmpv)

    testImplementation(kotlin("test"))
}
