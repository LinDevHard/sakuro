/**
 * The allowed project-to-project edges (ARCHITECTURE.md §3): a flat graph where
 * core modules depend only on `:core:core-model`, features only on core, and
 * engines/features never on each other. Unknown modules may depend on nothing,
 * so a temporary edge has to be listed in `config/module-graph-allowlist.txt`.
 */
object ModuleRules {

    private const val MODEL = ":core:core-model"
    private const val PLAYER = ":core:core-player"
    private const val UPSCALE = ":core:core-upscale"
    private const val DESIGN_SYSTEM = ":core:core-designsystem"
    private const val CORE_UI = ":core:core-ui"

    fun isAllowed(from: String, to: String): Boolean = when {
        from == to -> true
        from == MODEL || from == DESIGN_SYSTEM -> false
        from == CORE_UI -> to in setOf(MODEL, PLAYER, DESIGN_SYSTEM)
        from.isCore() -> to == MODEL
        from.isEngine() -> to in setOf(MODEL, PLAYER, UPSCALE) || to.isLib()
        from.isFeature() -> to.isCore()
        from == ":shared" -> to.isFeature() || to.isCore()
        from == ":androidApp" || from == ":desktopApp" -> to == ":shared" || to.isEngine() || to.isCore()
        else -> false
    }

    /** `:from -> :to` per line; `#` starts a comment, blank lines are ignored. */
    fun parseAllowlist(text: String): Set<Pair<String, String>> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split("->").map(String::trim)
            require(parts.size == 2 && parts.all { it.startsWith(":") }) { "Malformed allowlist line: '$line'" }
            parts[0] to parts[1]
        }
        .toSet()

    private fun String.isCore() = startsWith(":core:")
    private fun String.isEngine() = startsWith(":engine:")
    private fun String.isFeature() = startsWith(":feature:")
    private fun String.isLib() = startsWith(":lib:")
}
