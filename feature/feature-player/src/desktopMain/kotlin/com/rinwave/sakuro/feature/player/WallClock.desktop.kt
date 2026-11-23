package com.rinwave.sakuro.feature.player

import java.text.DateFormat
import java.util.Date

internal actual fun formatWallClock(epochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))
