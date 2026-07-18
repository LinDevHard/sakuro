package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable
import com.rinwave.sakuro.core.media.DateBucket
import com.rinwave.sakuro.core.media.LibrarySort
import com.rinwave.sakuro.core.player.EngineType
import com.rinwave.sakuro.core.upscale.BundledShaderRole
import com.rinwave.sakuro.core.upscale.ContentClass
import com.rinwave.sakuro.core.upscale.ShaderCost
import com.rinwave.sakuro.core.upscale.UpscalePass
import com.rinwave.sakuro.core.upscale.UpscaleProfile
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.chain_denoise
import sakuro.composeapp.generated.resources.chain_none
import sakuro.composeapp.generated.resources.chain_sharpness
import sakuro.composeapp.generated.resources.chain_upscale
import sakuro.composeapp.generated.resources.content_anime
import sakuro.composeapp.generated.resources.content_any
import sakuro.composeapp.generated.resources.content_cartoon
import sakuro.composeapp.generated.resources.content_live_action
import sakuro.composeapp.generated.resources.engine_fake
import sakuro.composeapp.generated.resources.engine_fake_hint
import sakuro.composeapp.generated.resources.engine_media3
import sakuro.composeapp.generated.resources.engine_media3_hint
import sakuro.composeapp.generated.resources.engine_mpv
import sakuro.composeapp.generated.resources.engine_mpv_hint
import sakuro.composeapp.generated.resources.preset_anime_hd_desc
import sakuro.composeapp.generated.resources.preset_anime_hd_name
import sakuro.composeapp.generated.resources.preset_anime_sd_desc
import sakuro.composeapp.generated.resources.preset_anime_sd_name
import sakuro.composeapp.generated.resources.preset_auto_desc
import sakuro.composeapp.generated.resources.preset_auto_name
import sakuro.composeapp.generated.resources.preset_live_light_desc
import sakuro.composeapp.generated.resources.preset_live_light_name
import sakuro.composeapp.generated.resources.preset_off_desc
import sakuro.composeapp.generated.resources.preset_off_name
import sakuro.composeapp.generated.resources.section_earlier
import sakuro.composeapp.generated.resources.section_this_month
import sakuro.composeapp.generated.resources.section_this_week
import sakuro.composeapp.generated.resources.section_today
import sakuro.composeapp.generated.resources.section_yesterday
import sakuro.composeapp.generated.resources.shader_cost_high
import sakuro.composeapp.generated.resources.shader_cost_low
import sakuro.composeapp.generated.resources.shader_cost_medium
import sakuro.composeapp.generated.resources.shader_role_denoise
import sakuro.composeapp.generated.resources.shader_role_sharpen
import sakuro.composeapp.generated.resources.shader_role_upscale
import sakuro.composeapp.generated.resources.shader_role_utility
import sakuro.composeapp.generated.resources.sort_field_date_added
import sakuro.composeapp.generated.resources.sort_field_duration
import sakuro.composeapp.generated.resources.sort_field_name
import sakuro.composeapp.generated.resources.sort_field_resolution
import sakuro.composeapp.generated.resources.sort_field_size

/**
 * Localized display strings for core enums and models. Core keeps stable keys/enums;
 * the UI resolves them to text via Compose Resources so everything follows the locale.
 */

@Composable
fun LibrarySort.label(): String = stringResource(
    when (this) {
        LibrarySort.DateAdded -> Res.string.sort_field_date_added
        LibrarySort.Name -> Res.string.sort_field_name
        LibrarySort.Size -> Res.string.sort_field_size
        LibrarySort.Duration -> Res.string.sort_field_duration
        LibrarySort.Resolution -> Res.string.sort_field_resolution
    },
)

@Composable
fun DateBucket.label(): String = stringResource(
    when (this) {
        DateBucket.TODAY -> Res.string.section_today
        DateBucket.YESTERDAY -> Res.string.section_yesterday
        DateBucket.THIS_WEEK -> Res.string.section_this_week
        DateBucket.THIS_MONTH -> Res.string.section_this_month
        DateBucket.EARLIER -> Res.string.section_earlier
    },
)

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

internal fun formatPercent(value: Float): String = "${(value * PERCENT).toInt()}%"

/** "1", "1.25" — a multiplier without trailing zeroes for sensitivity and upscale factor. */
internal fun formatMultiplier(value: Float): String {
    val rounded = (value * PERCENT).toInt()
    return if (rounded % PERCENT == 0) "${rounded / PERCENT}" else (rounded / PERCENT.toFloat()).toString()
}
