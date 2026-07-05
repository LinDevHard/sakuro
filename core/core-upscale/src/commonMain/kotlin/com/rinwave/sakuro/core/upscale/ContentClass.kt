package com.rinwave.sakuro.core.upscale

import kotlinx.serialization.Serializable

/**
 * Image style — the primary class for preset selection (FEATURES.md §1.1).
 * "Movie" is modeled as [LIVE_ACTION] + film flags; there is no separate class.
 */
@Serializable
enum class ContentClass {
    ANIME,
    CARTOON,
    LIVE_ACTION,
    UNKNOWN,
}
