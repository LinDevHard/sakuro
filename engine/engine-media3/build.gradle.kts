plugins {
    id("sakuro.android.library")
}

android {
    namespace = "com.rinwave.sakuro.engine.media3"
}

dependencies {
    api(project(":core:core-player"))
    implementation(project(":core:core-upscale"))
    api(project(":lib:media3-usershader"))
    implementation(libs.kotlinx.coroutines.android)
    api(libs.media3.exoplayer)
    // Network streams: DefaultMediaSourceFactory picks these up by reflection.
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    // Background playback: MediaSessionService, notification, lock screen and headset controls.
    implementation(libs.media3.session)
    implementation(libs.media3.effect)
    api(libs.media3.common)

    testImplementation(kotlin("test"))
}
