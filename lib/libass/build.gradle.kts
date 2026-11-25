plugins {
    id("sakuro.android.library")
}

// libass and its native stack (freetype, harfbuzz, fribidi, fontconfig, expat, libunibreak),
// built from the pinned sources in third_party/libass-android (git submodules) so F-Droid can
// rebuild every native byte. Mirrors upstream lib_ass/build.gradle.kts; upstream's own Gradle
// build cannot be included because it uses a different AGP.
val upstream = rootProject.file("third_party/libass-android/lib_ass")

android {
    namespace = "io.github.peerless2012.ass"
    ndkVersion = libs.versions.ndk.get()
    defaultConfig {
        upstream.resolve("consumer-rules.pro").takeIf { it.exists() }?.let { consumerProguardFiles(it) }
        ndk { abiFilters += NativeAbis.ALL }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                // Reproducible native output: no checkout paths in the binaries.
                cFlags += NativeAbis.reproducibleFlags(rootDir)
                cppFlags += NativeAbis.reproducibleFlags(rootDir)
            }
        }
    }
    buildTypes {
        release {
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                    // Reproducibility: the linker hashes the unstripped file (debug info with
                    // AGP's per-checkout .cxx hash and the CMake-built deps' absolute paths) into
                    // the GNU build-id. The shipped .so is stripped, so drop the build-id instead.
                    arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=none"
                    cFlags += "-g0"
                    cppFlags += "-g0"
                }
            }
        }
    }
    sourceSets["main"].manifest.srcFile(upstream.resolve("src/main/AndroidManifest.xml"))
    externalNativeBuild {
        cmake {
            path = upstream.resolve("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
    buildFeatures { prefabPublishing = true }
    prefab {
        create("ass") { headers = upstream.resolve("src/main/cpp/include").path }
    }
}

// libass-cmake builds several components with autotools; their `configure` scripts are
// generated, not checked in, so create them before CMake configures the native build.
val prepareLibassSources by tasks.registering(Exec::class) {
    description = "Runs the autogen step of the vendored libass native stack."
    commandLine(rootProject.file("tools/native/prepare-libass.sh"))
    inputs.file(rootProject.file("tools/native/prepare-libass.sh"))
    outputs.upToDateWhen { false }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name.startsWith("externalNativeBuild")) {
        dependsOn(prepareLibassSources)
    }
}
