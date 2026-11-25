package com.rinwave.sakuro.engine.media3

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Shifts the audio clock, which is the playback clock: video frames are released against it.
 * Reporting the position [delayUs] ahead makes the picture run that far ahead of the sound,
 * so the sound plays [delayUs] later (negative: earlier). The player's position stays the
 * picture's position. A smaller delay holds the clock (Media3 never lets it go backwards)
 * until the sound catches up: a short freeze of the picture, no seek, no rebuffer.
 */
@UnstableApi
internal class AudioDelaySink(sink: AudioSink, private val delayUs: () -> Long) : ForwardingAudioSink(sink) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else position + delayUs()
    }
}
