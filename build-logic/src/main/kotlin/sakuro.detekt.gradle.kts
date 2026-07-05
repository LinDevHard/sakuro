import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// detekt + ktlint rules (detekt-formatting) for all modules.
// No type resolution: all sources under src/ are analyzed (all KMP source sets).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

private val libs = the<VersionCatalogsExtension>().named("libs")

configure<DetektExtension> {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true // ktlint (formatting) rules fix files themselves
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(files("src"))
}

dependencies {
    "detektPlugins"(libs.findLibrary("detekt-formatting").get())
}
