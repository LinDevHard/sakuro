rootProject.name = "sakuro"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":core:core-designsystem")
include(":core:core-model")
include(":core:core-ui")
include(":core:core-player")
include(":core:core-upscale")
include(":core:core-media")
include(":core:core-detect")
include(":core:core-settings")
include(":engine:engine-media3")
include(":engine:engine-mpv")
include(":lib:media3-usershader")
// Named lib_ass because the prefab package name is the project name, and upstream's
// libass-kt CMake looks for `find_package(lib_ass)`.
include(":lib:lib_ass")
project(":lib:lib_ass").projectDir = file("lib/libass")
include(":lib:libass-kt")
include(":lib:libass-media")
include(":engine:engine-fake")
include(":feature:feature-bench")
include(":feature:feature-library")
include(":feature:feature-player")
include(":feature:feature-settings")
