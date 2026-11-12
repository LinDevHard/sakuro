import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleRulesTest {

    @Test
    fun coreModelDependsOnNothing() {
        assertFalse(ModuleRules.isAllowed(":core:core-model", ":core:core-player"))
    }

    @Test
    fun coreModulesMayOnlyDependOnModel() {
        assertTrue(ModuleRules.isAllowed(":core:core-player", ":core:core-model"))
        assertFalse(ModuleRules.isAllowed(":core:core-player", ":core:core-upscale"))
        assertFalse(ModuleRules.isAllowed(":core:core-data", ":core:core-upscale"))
    }

    @Test
    fun coreUiMayUseModelPlayerAndDesignSystem() {
        assertTrue(ModuleRules.isAllowed(":core:core-ui", ":core:core-model"))
        assertTrue(ModuleRules.isAllowed(":core:core-ui", ":core:core-player"))
        assertTrue(ModuleRules.isAllowed(":core:core-ui", ":core:core-designsystem"))
        assertFalse(ModuleRules.isAllowed(":core:core-ui", ":core:core-data"))
        assertFalse(ModuleRules.isAllowed(":core:core-designsystem", ":core:core-model"))
    }

    @Test
    fun featuresDependOnCoreOnly() {
        assertTrue(ModuleRules.isAllowed(":feature:feature-player", ":core:core-data"))
        assertFalse(ModuleRules.isAllowed(":feature:feature-player", ":feature:feature-library"))
        assertFalse(ModuleRules.isAllowed(":feature:feature-player", ":engine:engine-media3"))
    }

    @Test
    fun enginesUsePlayerUpscaleModelAndLibs() {
        assertTrue(ModuleRules.isAllowed(":engine:engine-media3", ":core:core-upscale"))
        assertTrue(ModuleRules.isAllowed(":engine:engine-media3", ":lib:media3-usershader"))
        assertFalse(ModuleRules.isAllowed(":engine:engine-media3", ":core:core-data"))
        assertFalse(ModuleRules.isAllowed(":engine:engine-mpv", ":engine:engine-media3"))
    }

    @Test
    fun sharedAndAppsSitOnTop() {
        assertTrue(ModuleRules.isAllowed(":shared", ":feature:feature-player"))
        assertFalse(ModuleRules.isAllowed(":shared", ":engine:engine-fake"))
        assertTrue(ModuleRules.isAllowed(":androidApp", ":shared"))
        assertTrue(ModuleRules.isAllowed(":androidApp", ":engine:engine-mpv"))
        assertFalse(ModuleRules.isAllowed(":androidApp", ":feature:feature-player"))
        assertTrue(ModuleRules.isAllowed(":desktopApp", ":engine:engine-fake"))
    }

    @Test
    fun libsAndUnknownModulesDependOnNothing() {
        assertFalse(ModuleRules.isAllowed(":lib:media3-usershader", ":core:core-model"))
        assertFalse(ModuleRules.isAllowed(":composeApp", ":core:core-model"))
    }

    @Test
    fun parsesAllowlistIgnoringCommentsAndBlanks() {
        val edges = ModuleRules.parseAllowlist(
            """
            # temporary
            :core:core-player -> :core:core-upscale

            :shared -> :engine:engine-fake   # until Task 10
            """.trimIndent(),
        )
        assertEquals(
            setOf(
                ":core:core-player" to ":core:core-upscale",
                ":shared" to ":engine:engine-fake",
            ),
            edges,
        )
    }
}
