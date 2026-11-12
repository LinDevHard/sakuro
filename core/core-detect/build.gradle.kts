plugins {
    id("sakuro.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:core-model"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
