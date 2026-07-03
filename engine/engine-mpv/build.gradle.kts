plugins {
    id("sakuro.android.library")
}

android {
    namespace = "com.rinwave.sakuro.engine.mpv"
}

dependencies {
    api(project(":core:core-player"))
    implementation(libs.kotlinx.coroutines.android)
    // Разбор свойства mpv `track-list` (JSON) без @Serializable-классов.
    implementation(libs.kotlinx.serialization.json)
    api(libs.jdtech.libmpv)

    testImplementation(kotlin("test"))
}
