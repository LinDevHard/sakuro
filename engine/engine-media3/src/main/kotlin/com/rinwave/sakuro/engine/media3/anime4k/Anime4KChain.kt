package com.rinwave.sakuro.engine.media3.anime4k

import android.content.Context
import android.util.Log
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import java.io.IOException

/**
 * Selects and loads the Anime4K `.glsl` chain for an [UpscaleProfile] on the Media3 engine.
 *
 * The order and S/M model selection mirror the mpv engine:
 * Clamp→Denoise→Restore→Upscale, with CNN size selected by pass strength.
 * Assets come from `assets/anime4k/` (vendored by the engine-mpv module; in a built
 * app the modules' assets are merged into a single [android.content.res.AssetManager]).
 */
internal object Anime4KChain {

    private const val ASSET_DIR = "anime4k"

    /** Sharpen-strength threshold at or above which the medium CNN model is used instead of the small one. */
    private const val HEAVY_SHARPEN = 0.6f

    /** Upscale-factor threshold at or above which the medium CNN model is used instead of the small one. */
    private const val HEAVY_UPSCALE = 1.75f

    /** The `.glsl` file names of the chain for a profile (empty — Anime4K is not applicable). */
    fun shaderFilesFor(profile: UpscaleProfile): List<String> {
        val anime = profile.contentClass == ContentClass.ANIME || profile.contentClass == ContentClass.CARTOON
        if (!anime || profile.passes.isEmpty()) return emptyList()
        val chain = buildList {
            profile.passes.filterIsInstance<UpscalePass.Denoise>().firstOrNull()?.let {
                add("Anime4K_Denoise_Bilateral_Mode.glsl")
            }
            profile.passes.filterIsInstance<UpscalePass.Sharpen>().firstOrNull()?.let {
                add(if (it.strength >= HEAVY_SHARPEN) "Anime4K_Restore_CNN_M.glsl" else "Anime4K_Restore_CNN_S.glsl")
            }
            profile.passes.filterIsInstance<UpscalePass.Upscale>().firstOrNull()?.let {
                val heavy = it.factor >= HEAVY_UPSCALE
                add(if (heavy) "Anime4K_Upscale_CNN_x2_M.glsl" else "Anime4K_Upscale_CNN_x2_S.glsl")
            }
        }
        if (chain.isEmpty()) return emptyList()
        // Highlight clamping protects the CNN passes from ringing on blown-out lines.
        return listOf("Anime4K_Clamp_Highlights.glsl") + chain
    }

    /**
     * Loads and parses the chain for a profile into a flat list of passes.
     * The files' passes are concatenated in [shaderFilesFor] order. An empty list —
     * Anime4K is not applied (the profile is not anime/cartoon, or has no passes).
     */
    fun load(context: Context, profile: UpscaleProfile): List<UserShaderPass> {
        val files = shaderFilesFor(profile)
        if (files.isEmpty()) return emptyList()
        return try {
            files.flatMap { file ->
                val source = context.assets.open("$ASSET_DIR/$file").bufferedReader().use { it.readText() }
                MpvUserShaderParser.parse(source)
            }
        } catch (e: IOException) {
            // Anime4K assets are not in this build (no engine-mpv module) — fall back to the legacy chain.
            Log.w(TAG, "Anime4K assets unavailable, falling back to legacy: ${e.message}")
            emptyList()
        }
    }

    private const val TAG = "Anime4KChain"
}
