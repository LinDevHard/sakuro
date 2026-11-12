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
include(":engine:engine-fake")
