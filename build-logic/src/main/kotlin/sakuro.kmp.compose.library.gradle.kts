// KMP library with Compose Multiplatform UI and its own Compose Resources.
// The generated Res class lives in com.rinwave.sakuro.<group>.<name>.resources,
// derived from the project path (":core:core-ui" -> core.ui).
plugins {
    id("sakuro.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.rinwave.sakuro." + resourcePackageSuffix(path) + ".resources"
}

fun resourcePackageSuffix(projectPath: String): String {
    val parts = projectPath.trim(':').split(':')
    val group = parts.first()
    val name = parts.last().removePrefix("$group-").replace("-", "")
    return if (parts.size == 1) group else "$group.$name"
}
