plugins {
    `kotlin-dsl`
}

dependencies {
    // precompiled script plugins require implementation (not compileOnly):
    // the applied plugins are resolved from build-logic's own classpath
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}
