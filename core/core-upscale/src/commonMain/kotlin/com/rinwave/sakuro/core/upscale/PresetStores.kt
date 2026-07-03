package com.rinwave.sakuro.core.upscale

/** Персистентность пресетов одним узлом DI: свои пресеты + пины к файлам. */
data class PresetStores(
    val user: UserPresetStore,
    val pinned: PinnedPresetStore,
)
