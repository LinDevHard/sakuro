plugins {
    `kotlin-dsl`
}

dependencies {
    // precompiled script plugins требуют implementation (не compileOnly):
    // применяемые плагины резолвятся из classpath самого build-logic
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}
