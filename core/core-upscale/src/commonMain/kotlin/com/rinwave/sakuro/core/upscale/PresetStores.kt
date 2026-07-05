package com.rinwave.sakuro.core.upscale

/** Preset persistence under a single DI node: user presets + file pins. */
data class PresetStores(
    val user: UserPresetStore,
    val pinned: PinnedPresetStore,
)
