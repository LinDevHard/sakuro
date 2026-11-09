plugins {
    id("sakuro.android.library")
}

// mpv user-shader runtime for Media3: parses mpv `.glsl` hooks and runs the
// multi-pass graph as a single GlEffect. Independent of Sakuro code on purpose,
// so it can later be published on its own.
android {
    namespace = "com.rinwave.media3.usershader"
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.media3.effect)
    api(libs.media3.common)

    testImplementation(kotlin("test"))
}
