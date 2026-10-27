package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

/** Changes only the text renderer's clock; audio/video renderers receive the original positions. */
@UnstableApi
internal class SubtitleRenderersFactory(context: Context, private val subtitlePositionUs: (Long) -> Long) :
    DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val renderers = arrayListOf<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, renderers)
        renderers.forEach { renderer ->
            out += if (renderer is TextRenderer) SubtitleDelayRenderer(renderer, subtitlePositionUs) else renderer
        }
    }
}

@UnstableApi
private class SubtitleDelayRenderer(
    private val text: TextRenderer,
    private val subtitlePositionUs: (Long) -> Long,
) : Renderer by text {
    private var timeline: Timeline = Timeline.EMPTY
    private var periodId: MediaSource.MediaPeriodId? = null
    private var streamOffsetUs = 0L

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        text.render(subtitlePositionUs(positionUs), elapsedRealtimeUs)
    }

    override fun resetPosition(positionUs: Long) = text.resetPosition(subtitlePositionUs(positionUs))

    override fun enable(
        configuration: RendererConfiguration,
        formats: Array<Format>,
        stream: SampleStream,
        positionUs: Long,
        joining: Boolean,
        mayRenderStartOfStream: Boolean,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        periodId = mediaPeriodId
        streamOffsetUs = offsetUs
        text.enable(
            configuration, formats, stream, subtitlePositionUs(positionUs), joining,
            mayRenderStartOfStream, startPositionUs, offsetUs, mediaPeriodId,
        )
    }

    override fun replaceStream(
        formats: Array<Format>, stream: SampleStream, startPositionUs: Long, offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        periodId = mediaPeriodId
        streamOffsetUs = offsetUs
        text.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun setTimeline(timeline: Timeline) {
        this.timeline = timeline
        text.setTimeline(timeline)
        if (text.isCurrentStreamFinal) updateFinalStreamEnd()
    }

    override fun setCurrentStreamFinal() {
        text.setCurrentStreamFinal()
        // RendererHolder only supplies this to concrete TextRenderers. Preserve it for our wrapper.
        updateFinalStreamEnd()
    }

    private fun updateFinalStreamEnd() {
        val id = periodId ?: return
        val index = timeline.getIndexOfPeriod(id.periodUid)
        if (index == C.INDEX_UNSET) return
        val period = timeline.getPeriod(index, Timeline.Period())
        val durationUs = if (id.isAd) {
            period.getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup)
        } else {
            period.durationUs
        }
        if (durationUs != C.TIME_UNSET) {
            text.setFinalStreamEndPositionUs(subtitlePositionUs(streamOffsetUs + durationUs))
        }
    }
}
