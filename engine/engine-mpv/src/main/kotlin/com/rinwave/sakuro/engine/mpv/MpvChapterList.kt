package com.rinwave.sakuro.engine.mpv

import com.rinwave.sakuro.core.player.Chapter
import com.rinwave.sakuro.core.player.Chapters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Parses mpv's `chapter-list` property: `[{"title": "...", "time": 12.5}, ...]` (time in seconds). */
internal fun parseMpvChapterList(json: String): List<Chapter> {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
    val chapters = root.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        val seconds = entry["time"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        Chapter(
            startMs = (seconds * MS_PER_SECOND).toLong(),
            title = entry["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
    return Chapters.normalize(chapters)
}

private const val MS_PER_SECOND = 1000.0
