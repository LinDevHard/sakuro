package com.rinwave.sakuro.feature.player

/** Local wall-clock time ("14:32") in the device's 12/24-hour style, for live DVR positions. */
internal expect fun formatWallClock(epochMs: Long): String
