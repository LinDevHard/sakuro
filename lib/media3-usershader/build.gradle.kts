plugins {
    id("sakuro.android.library")
}

// mpv user-shader runtime for Media3: parses mpv `.glsl` hooks and runs the
// multi-pass graph as a single GlEffect. Independent of Sakuro code on purpose,
// so it can later be published on its own.
android {
    namespace = "com.rinwave.media3.usershader"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // On-device GL tests reuse the JVM tests' third-party shader fixtures.
    sourceSets["androidTest"].assets.srcDir("src/test/resources")
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.media3.effect)
    api(libs.media3.common)

    testImplementation(kotlin("test"))

    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
