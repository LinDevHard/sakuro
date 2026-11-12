import org.gradle.api.artifacts.ProjectDependency

// Enforces ModuleRules on this project's declared project dependencies.
// Temporary exceptions live in config/module-graph-allowlist.txt; an entry that
// no longer matches a real edge fails too, so the allowlist can only shrink.
val allowlistFile = rootProject.file("config/module-graph-allowlist.txt")
val projectPath = path
val knownProjects = rootProject.allprojects.map { it.path }.toSet()
val declaredConfigurations = configurations

tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Fails on project dependencies that break the flat module graph."
    inputs.file(allowlistFile).optional()
    doLast {
        val allowlist = if (allowlistFile.exists()) ModuleRules.parseAllowlist(allowlistFile.readText()) else emptySet()
        val edges = declaredConfigurations
            .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
            .map { projectPath to it.path }
            .filter { (from, to) -> from != to }
            .toSet()
        val violations = edges.filter { (from, to) -> !ModuleRules.isAllowed(from, to) && (from to to) !in allowlist }
        val stale = allowlist.filter { (from, to) ->
            from !in knownProjects || (from == projectPath && ((from to to) !in edges || ModuleRules.isAllowed(from, to)))
        }
        val problems = violations.map { "forbidden edge ${it.first} -> ${it.second}" } +
            stale.map { "stale allowlist entry ${it.first} -> ${it.second}" }
        check(problems.isEmpty()) { problems.joinToString(separator = "\n", prefix = "Module graph:\n") }
    }
}
