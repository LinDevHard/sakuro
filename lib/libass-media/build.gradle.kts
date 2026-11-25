plugins {
    id("sakuro.android.library")
}

// Media3 integration of libass (upstream lib_ass_media): MKV extractor with font attachments,
// ASS parser factory, renderer and the overlay views. Built from third_party sources.
val upstream = rootProject.file("third_party/libass-android/lib_ass_media")

android {
    namespace = "io.github.peerless2012.exo"
    defaultConfig {
        upstream.resolve("consumer-rules.pro").takeIf { it.exists() }?.let { consumerProguardFiles(it) }
    }
    sourceSets["main"].apply {
        manifest.srcFile(upstream.resolve("src/main/AndroidManifest.xml"))
        // Upstream keeps Kotlin under src/main/java; AGP built-in Kotlin reads kotlin dirs.
        kotlin.directories += upstream.resolve("src/main/java").path
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.annotation.experimental)
    api(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.effect)
    api(project(":lib:libass-kt"))
    runtimeOnly(project(":lib:lib_ass"))
}
