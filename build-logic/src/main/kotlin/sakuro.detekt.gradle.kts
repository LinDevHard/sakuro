import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// detekt + ktlint-правила (detekt-formatting) для всех модулей.
// Без type resolution: анализируются все исходники под src/ (все KMP source set'ы).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

configure<DetektExtension> {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true // ktlint-правила (formatting) правят файлы сами
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src"))
}

dependencies {
    "detektPlugins"(libs.findLibrary("detekt-formatting").get())
}
