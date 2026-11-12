package com.rinwave.sakuro.core.ui

import androidx.compose.runtime.Composable
import com.rinwave.sakuro.core.model.ContentClass
import com.rinwave.sakuro.core.model.EngineType
import com.rinwave.sakuro.core.model.UpscalePass
import com.rinwave.sakuro.core.model.UpscaleProfile
import com.rinwave.sakuro.core.ui.resources.Res
import com.rinwave.sakuro.core.ui.resources.chain_denoise
import com.rinwave.sakuro.core.ui.resources.chain_none
import com.rinwave.sakuro.core.ui.resources.chain_sharpness
import com.rinwave.sakuro.core.ui.resources.chain_upscale
import com.rinwave.sakuro.core.ui.resources.content_anime
import com.rinwave.sakuro.core.ui.resources.content_any
import com.rinwave.sakuro.core.ui.resources.content_cartoon
import com.rinwave.sakuro.core.ui.resources.content_live_action
import com.rinwave.sakuro.core.ui.resources.engine_fake
import com.rinwave.sakuro.core.ui.resources.engine_fake_hint
import com.rinwave.sakuro.core.ui.resources.engine_media3
import com.rinwave.sakuro.core.ui.resources.engine_media3_hint
import com.rinwave.sakuro.core.ui.resources.engine_mpv
import com.rinwave.sakuro.core.ui.resources.engine_mpv_hint
import com.rinwave.sakuro.core.ui.resources.preset_anime_hd_desc
import com.rinwave.sakuro.core.ui.resources.preset_anime_hd_name
import com.rinwave.sakuro.core.ui.resources.preset_anime_sd_desc
import com.rinwave.sakuro.core.ui.resources.preset_anime_sd_name
import com.rinwave.sakuro.core.ui.resources.preset_auto_desc
import com.rinwave.sakuro.core.ui.resources.preset_auto_name
import com.rinwave.sakuro.core.ui.resources.preset_live_light_desc
import com.rinwave.sakuro.core.ui.resources.preset_live_light_name
import com.rinwave.sakuro.core.ui.resources.preset_off_desc
import com.rinwave.sakuro.core.ui.resources.preset_off_name
import com.rinwave.sakuro.core.ui.resources.shader_cost_high
import com.rinwave.sakuro.core.ui.resources.shader_cost_low
import com.rinwave.sakuro.core.ui.resources.shader_cost_medium
import com.rinwave.sakuro.core.ui.resources.shader_role_denoise
import com.rinwave.sakuro.core.ui.resources.shader_role_sharpen
import com.rinwave.sakuro.core.ui.resources.shader_role_upscale
import com.rinwave.sakuro.core.ui.resources.shader_role_utility
import com.rinwave.sakuro.core.upscale.BundledShaderRole
import com.rinwave.sakuro.core.upscale.ShaderCost
import org.jetbrains.compose.resources.stringResource

/**
 * Localized display strings for core enums and models. Core keeps stable keys/enums;
 * the UI resolves them to text via Compose Resources so everything follows the locale.
 */

@Composable
fun ContentClass.label(): String = stringResource(
    when (this) {
        ContentClass.ANIME -> Res.string.content_anime
        ContentClass.CARTOON -> Res.string.content_cartoon
        ContentClass.LIVE_ACTION -> Res.string.content_live_action
        ContentClass.UNKNOWN -> Res.string.content_any
    },
)

@Composable
fun BundledShaderRole.label(): String = stringResource(
    when (this) {
        BundledShaderRole.UPSCALE -> Res.string.shader_role_upscale
        BundledShaderRole.SHARPEN -> Res.string.shader_role_sharpen
        BundledShaderRole.DENOISE -> Res.string.shader_role_denoise
        BundledShaderRole.UTILITY -> Res.string.shader_role_utility
    },
)

@Composable
fun ShaderCost.label(): String = stringResource(
    when (this) {
        ShaderCost.LOW -> Res.string.shader_cost_low
        ShaderCost.MEDIUM -> Res.string.shader_cost_medium
        ShaderCost.HIGH -> Res.string.shader_cost_high
    },
)

@Composable
fun EngineType.label(): String = stringResource(
    when (this) {
        EngineType.MPV -> Res.string.engine_mpv
        EngineType.MEDIA3 -> Res.string.engine_media3
        EngineType.FAKE -> Res.string.engine_fake
    },
)

@Composable
fun EngineType.hint(): String = stringResource(
    when (this) {
        EngineType.MPV -> Res.string.engine_mpv_hint
        EngineType.MEDIA3 -> Res.string.engine_media3_hint
        EngineType.FAKE -> Res.string.engine_fake_hint
    },
)

/** Localized display name: built-in presets use resources, user presets keep their stored name. */
@Composable
fun UpscaleProfile.displayName(): String = when (id) {
    "off" -> stringResource(Res.string.preset_off_name)
    "anime-sd" -> stringResource(Res.string.preset_anime_sd_name)
    "anime-hd" -> stringResource(Res.string.preset_anime_hd_name)
    "live-light" -> stringResource(Res.string.preset_live_light_name)
    "auto" -> stringResource(Res.string.preset_auto_name)
    else -> name
}

/** Localized description: built-in/auto use resources, user presets summarize their passes. */
@Composable
fun UpscaleProfile.displayDescription(): String = when (id) {
    "off" -> stringResource(Res.string.preset_off_desc)
    "anime-sd" -> stringResource(Res.string.preset_anime_sd_desc)
    "anime-hd" -> stringResource(Res.string.preset_anime_hd_desc)
    "live-light" -> stringResource(Res.string.preset_live_light_desc)
    "auto" -> stringResource(Res.string.preset_auto_desc)
    // A custom shader chain replaces the pass processing — show its files.
    else -> if (shaderChain.isNotEmpty()) shaderChain.joinToString(" → ") else passesSummary(passes)
}

/** Localized "upscale ×2 · sharpness 80%" summary of a preset's passes. */
@Composable
fun passesSummary(passes: List<UpscalePass>): String {
    if (passes.isEmpty()) return stringResource(Res.string.chain_none)
    val parts = ArrayList<String>(passes.size)
    for (pass in passes) {
        parts += when (pass) {
            is UpscalePass.Upscale -> stringResource(Res.string.chain_upscale, formatMultiplier(pass.factor))
            is UpscalePass.Sharpen -> stringResource(Res.string.chain_sharpness, formatPercent(pass.strength))
            is UpscalePass.Denoise -> stringResource(Res.string.chain_denoise, formatPercent(pass.strength))
        }
    }
    return parts.joinToString(" · ")
}

private const val PERCENT = 100

fun formatPercent(value: Float): String = "${(value * PERCENT).toInt()}%"

/** "1", "1.25" — a multiplier without trailing zeroes for sensitivity and upscale factor. */
fun formatMultiplier(value: Float): String {
    val rounded = (value * PERCENT).toInt()
    return if (rounded % PERCENT == 0) "${rounded / PERCENT}" else (rounded / PERCENT.toFloat()).toString()
}
