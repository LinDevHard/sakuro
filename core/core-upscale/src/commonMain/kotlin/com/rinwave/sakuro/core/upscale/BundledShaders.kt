package com.rinwave.sakuro.core.upscale

/** What a bundled shader does in a chain — mirrors the abstract [UpscalePass] kinds. */
enum class BundledShaderRole { UPSCALE, SHARPEN, DENOISE, UTILITY }

/** Rough GPU cost tier — for adaptive degradation ordering and UI hints. */
enum class ShaderCost { LOW, MEDIUM, HIGH }

/**
 * A shader vendored into the app (FEATURES.md §2): available in preset chains
 * without importing, next to the user's own files. Chains reference shaders by
 * bare file name; an imported file with the same name shadows the bundled one
 * (engines resolve the imported directory first).
 */
data class BundledShader(
    val id: String,
    /** Human-readable name shown in pickers (a proper noun — not localized). */
    val displayName: String,
    /** Path inside the app's merged assets (`dir/file.glsl`). */
    val assetPath: String,
    val role: BundledShaderRole,
    val cost: ShaderCost,
    /** Content the shader is designed for; null — universal. */
    val contentClasses: Set<ContentClass>? = null,
    /**
     * Needs an ES 3.1 context on Media3 (`//!COMPUTE`/SSBO); libmpv runs it
     * regardless. On ES 3.0 the Media3 runtime degrades the chain to passthrough.
     */
    val requiresEs31: Boolean = false,
    /** `//!PARAM` name a pass strength 0..1 maps onto; null — not tunable. */
    val strengthParam: String? = null,
) {
    val fileName: String get() = assetPath.substringAfterLast('/')
}

/**
 * The registry of vendored shaders. Sources and licenses:
 * - Anime4K (`assets/anime4k`, MIT, `licenses/anime4k`);
 * - AMD FidelityFX CAS mpv port by agyild (MIT, `licenses/cas`), modified:
 *   the no-scale `//!WHEN` gates removed, `SHARPENING` exposed as `//!PARAM`;
 * - ravu by bjin (LGPL-3.0, `licenses/ravu`), unmodified;
 * - Sakuro's own bilateral denoise (GPL-3.0, the project license).
 */
object BundledShaders {

    /** Asset directory of the shaders vendored by this registry (not Anime4K's). */
    const val ASSET_DIR = "shaders_bundled"

    private val ANIME = setOf(ContentClass.ANIME, ContentClass.CARTOON)

    val all: List<BundledShader> = listOf(
        BundledShader(
            id = "cas",
            displayName = "FidelityFX CAS",
            assetPath = "$ASSET_DIR/CAS.glsl",
            role = BundledShaderRole.SHARPEN,
            cost = ShaderCost.LOW,
            strengthParam = "SHARPENING",
        ),
        BundledShader(
            id = "sakuro-denoise",
            displayName = "Sakuro Denoise",
            assetPath = "$ASSET_DIR/Sakuro_Denoise_Bilateral.glsl",
            role = BundledShaderRole.DENOISE,
            cost = ShaderCost.LOW,
            strengthParam = "intensity",
        ),
        BundledShader(
            id = "ravu-lite-r3",
            displayName = "RAVU Lite r3 \u00d72",
            assetPath = "$ASSET_DIR/ravu-lite-r3.hook",
            role = BundledShaderRole.UPSCALE,
            cost = ShaderCost.MEDIUM,
            requiresEs31 = true,
        ),
        BundledShader(
            id = "ravu-r3",
            displayName = "RAVU r3 \u00d72",
            assetPath = "$ASSET_DIR/ravu-r3.hook",
            role = BundledShaderRole.UPSCALE,
            cost = ShaderCost.MEDIUM,
        ),
        BundledShader(
            id = "anime4k-clamp",
            displayName = "Anime4K Clamp",
            assetPath = "anime4k/Anime4K_Clamp_Highlights.glsl",
            role = BundledShaderRole.UTILITY,
            cost = ShaderCost.LOW,
            contentClasses = ANIME,
        ),
        BundledShader(
            id = "anime4k-denoise",
            displayName = "Anime4K Denoise",
            assetPath = "anime4k/Anime4K_Denoise_Bilateral_Mode.glsl",
            role = BundledShaderRole.DENOISE,
            cost = ShaderCost.LOW,
            contentClasses = ANIME,
        ),
        BundledShader(
            id = "anime4k-restore-s",
            displayName = "Anime4K Restore S",
            assetPath = "anime4k/Anime4K_Restore_CNN_S.glsl",
            role = BundledShaderRole.SHARPEN,
            cost = ShaderCost.MEDIUM,
            contentClasses = ANIME,
        ),
        BundledShader(
            id = "anime4k-restore-m",
            displayName = "Anime4K Restore M",
            assetPath = "anime4k/Anime4K_Restore_CNN_M.glsl",
            role = BundledShaderRole.SHARPEN,
            cost = ShaderCost.HIGH,
            contentClasses = ANIME,
        ),
        BundledShader(
            id = "anime4k-upscale-s",
            displayName = "Anime4K Upscale S \u00d72",
            assetPath = "anime4k/Anime4K_Upscale_CNN_x2_S.glsl",
            role = BundledShaderRole.UPSCALE,
            cost = ShaderCost.MEDIUM,
            contentClasses = ANIME,
        ),
        BundledShader(
            id = "anime4k-upscale-m",
            displayName = "Anime4K Upscale M \u00d72",
            assetPath = "anime4k/Anime4K_Upscale_CNN_x2_M.glsl",
            role = BundledShaderRole.UPSCALE,
            cost = ShaderCost.HIGH,
            contentClasses = ANIME,
        ),
    )

    fun byId(id: String): BundledShader? = all.firstOrNull { it.id == id }

    /** Chains address shaders by bare file name — the lookup engines use. */
    fun byFileName(name: String): BundledShader? = all.firstOrNull { it.fileName == name }
}
