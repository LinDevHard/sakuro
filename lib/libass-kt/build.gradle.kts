plugins {
    id("sakuro.android.library")
}

// Kotlin/JNI wrapper over libass (upstream lib_ass_kt), built from third_party sources.
val upstream = rootProject.file("third_party/libass-android/lib_ass_kt")

android {
    namespace = "io.github.peerless2012.ass.kt"
    ndkVersion = libs.versions.ndk.get()
    defaultConfig {
        upstream.resolve("consumer-rules.pro").takeIf { it.exists() }?.let { consumerProguardFiles(it) }
        ndk { abiFilters += NativeAbis.ALL }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
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
    sourceSets["main"].apply {
        manifest.srcFile(upstream.resolve("src/main/AndroidManifest.xml"))
        // Upstream keeps Kotlin under src/main/java; AGP built-in Kotlin reads kotlin dirs.
        kotlin.directories += upstream.resolve("src/main/java").path
    }
    externalNativeBuild {
        cmake {
            path = upstream.resolve("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
    buildFeatures { prefab = true }
}

dependencies {
    // Upstream uses compileOnly so the prefab package is visible; libass.so itself ships via
    // :lib:libass-media's runtime classpath.
    compileOnly(project(":lib:lib_ass"))
}
