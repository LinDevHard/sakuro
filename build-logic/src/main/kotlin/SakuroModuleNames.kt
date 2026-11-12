/**
 * The package suffix for a Sakuro module: ":core:core-ui" -> "core.ui",
 * ":feature:feature-player" -> "feature.player", ":shared" -> "shared".
 */
fun sakuroModuleSuffix(projectPath: String): String {
    val parts = projectPath.trim(':').split(':')
    val group = parts.first()
    val name = parts.last().removePrefix("$group-").replace("-", "")
    return if (parts.size == 1) group else "$group.$name"
}
