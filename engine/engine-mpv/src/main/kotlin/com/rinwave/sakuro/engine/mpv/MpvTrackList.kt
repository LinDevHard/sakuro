package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.player.TrackInfo
import com.rinwave.sakuro.core.player.TrackType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the mpv `track-list` property (a JSON array of tracks).
 * `id` in [TrackInfo] is the numeric mpv track id; selection is via the `vid`/`aid`/`sid` properties.
 */
internal fun parseMpvTrackList(json: String): List<TrackInfo> {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
    return root.mapNotNull { element -> (element as? JsonObject)?.toTrackInfo() }
}

private fun JsonObject.toTrackInfo(): TrackInfo? {
    val type = when (string("type")) {
        "video" -> TrackType.VIDEO
        "audio" -> TrackType.AUDIO
        "sub" -> TrackType.SUBTITLE
        else -> return null
    }
    val id = this["id"]?.jsonPrimitive?.intOrNull ?: return null
    val language = string("lang")
    return TrackInfo(
        id = id.toString(),
        type = type,
        label = string("title") ?: defaultLabel(type, language),
        language = language,
        selected = this["selected"]?.jsonPrimitive?.booleanOrNull ?: false,
    )
}

private fun JsonObject.defaultLabel(type: TrackType, language: String?): String {
    val codec = string("codec").orEmpty()
    return when (type) {
        TrackType.VIDEO -> "${string("demux-w")}x${string("demux-h")} $codec".trim()
        TrackType.AUDIO -> "${language ?: "audio"} ($codec ${string("demux-channel-count") ?: "?"}ch)"
        TrackType.SUBTITLE -> language ?: codec
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
