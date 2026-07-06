package com.rinwave.sakuro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.Minimize
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Volume2
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.navigation.PlayerComponent
import com.rinwave.sakuro.ui.ImmersiveMode
import com.rinwave.sakuro.ui.PipEffect
import com.rinwave.sakuro.ui.ScaleMode
import com.rinwave.sakuro.ui.VideoSurface
import com.rinwave.sakuro.ui.components.EclipseLoader
import com.rinwave.sakuro.ui.components.PlayPauseButton
import com.rinwave.sakuro.ui.components.SakuroSeekBar
import com.rinwave.sakuro.ui.components.SkipButton
import com.rinwave.sakuro.ui.components.YouTubeSeekOverlay
import com.rinwave.sakuro.ui.displayDescription
import com.rinwave.sakuro.ui.displayName
import com.rinwave.sakuro.ui.gestures.LevelSwipeSession
import com.rinwave.sakuro.ui.gestures.PinchZoomSession
import com.rinwave.sakuro.ui.gestures.PlayerGestureCallbacks
import com.rinwave.sakuro.ui.gestures.SeekSwipeSession
import com.rinwave.sakuro.ui.gestures.detectPlayerGestures
import com.rinwave.sakuro.ui.rememberIsInPip
import com.rinwave.sakuro.ui.rememberPlayerSystemControls
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatTime
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_back
import sakuro.composeapp.generated.resources.player_playback_speed
import sakuro.composeapp.generated.resources.player_pin_desc
import sakuro.composeapp.generated.resources.player_pin_title
import sakuro.composeapp.generated.resources.player_seek_back
import sakuro.composeapp.generated.resources.player_seek_forward
import sakuro.composeapp.generated.resources.player_stats_for_nerds
import sakuro.composeapp.generated.resources.player_upscale_preset
import sakuro.composeapp.generated.resources.scale_fill_screen
import sakuro.composeapp.generated.resources.scale_fit_to_screen
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CONTROLS_HIDE_DELAY_MS = 3500L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val DOUBLE_TAP_SEEK_SEC = 10
private const val INDICATOR_LINGER_MS = 600L
private const val DOUBLE_TAP_LINGER_MS = 650L
private val PLAYBACK_SPEED_OPTIONS = listOf(1f, 1.5f, 1.75f, 2f, 3f)

private enum class LevelControl {
    BRIGHTNESS,
    VOLUME,
}

/** Gesture feedback currently shown over the player. */
private sealed interface GestureIndicator {
    sealed interface Top : GestureIndicator

    data class Seek(val targetMs: Long, val deltaMs: Long) : Top
    data class Level(val control: LevelControl, val value: Float) : GestureIndicator
    data class Zoom(val mode: ScaleMode, val manual: Float, val totalFactor: Float) : Top
}

/**
 * Screen-fill multiplier for a [vw] x [vh] video inside a [cw] x [ch] container:
 * how much the fitted frame must grow to remove letterboxing by cropping.
 */
private fun fillFactorFor(vw: Int, vh: Int, cw: Int, ch: Int): Float {
    if (minOf(vw, vh, cw, ch) <= 0) return 1f
    val videoAspect = vw.toFloat() / vh
    val containerAspect = cw.toFloat() / ch
    return maxOf(videoAspect / containerAspect, containerAspect / videoAspect)
}

/** "x2.3" zoom multiplier with one decimal digit. */
private fun formatZoom(factor: Float): String {
    val tenths = (factor * 10f).roundToInt()
    return "×${tenths / 10}.${tenths % 10}"
}

private fun formatPlaybackSpeed(speed: Float): String = when {
    abs(speed - 1.75f) < 0.01f -> "1.75×"
    abs(speed - 1.5f) < 0.01f -> "1.5×"
    abs(speed - 1f) < 0.01f -> "1.0×"
    abs(speed - 2f) < 0.01f -> "2.0×"
    abs(speed - 3f) < 0.01f -> "3.0×"
    else -> "${(speed * 100f).roundToInt() / 100f}×"
}

/** Accumulated double-tap seek indicator, +/-N seconds per side. */
private data class DoubleTapSeek(
    val forward: Boolean,
    val totalSec: Int,
    val key: Int,
    val verticalFraction: Float,
)

@Composable
fun PlayerScreen(component: PlayerComponent) {
    val state by component.engine.state.collectAsState()
    val debugEnabled by component.debugOverlay.collectAsState()
    val selectedPresetId by component.selectedPresetId.collectAsState()
    val gesturesEnabled by component.gesturesEnabled.collectAsState()
    val gestureSensitivity by component.gestureSensitivity.collectAsState()

    val systemControls = rememberPlayerSystemControls()
    val haptics = LocalHapticFeedback.current

    // Enter PiP on backgrounding; the PiP window shows video only.
    PipEffect(
        isPlaying = state.isPlaying,
        videoWidth = state.videoWidth,
        videoHeight = state.videoHeight,
        onPlay = component.engine::play,
        onPause = component.engine::pause,
    )
    val inPip = rememberIsInPip()

    // Fullscreen immersive mode hides system bars in the player, outside PiP.
    ImmersiveMode(enabled = !inPip)
    var wasInPip by remember { mutableStateOf(inPip) }
    LaunchedEffect(inPip) {
        if (inPip != wasInPip) {
            wasInPip = inPip
            component.onPipModeChanged()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var presetSheetVisible by remember { mutableStateOf(false) }
    var speedSheetVisible by remember { mutableStateOf(false) }
    var speedBoost by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableStateOf(1f) }
    // YouTube-like frame scaling: the engine applies discrete crop first,
    // then [manualZoom] adds an overlay transform (1.0 means no extra zoom).
    var scaleMode by remember { mutableStateOf(ScaleMode.FIT) }
    var manualZoom by remember { mutableStateOf(1f) }

    var indicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var lingerKey by remember { mutableStateOf(0) }
    var doubleTapSeek by remember { mutableStateOf<DoubleTapSeek?>(null) }

    // Auto-hide controls during playback.
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Keep the gesture indicator briefly after the finger is released.
    LaunchedEffect(lingerKey) {
        if (lingerKey > 0) {
            delay(INDICATOR_LINGER_MS)
            if (!gestureActive) indicator = null
        }
    }

    // Fade the double-tap badge after the tap series ends.
    LaunchedEffect(doubleTapSeek?.key) {
        if (doubleTapSeek != null) {
            delay(DOUBLE_TAP_LINGER_MS)
            doubleTapSeek = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { component.onViewportSizeChanged(it.width, it.height) },
    ) {
        // The engine handles crop; manual zoom is a layer transform above it.
        VideoSurface(
            component.engine,
            scaleMode,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = manualZoom
                    scaleY = manualZoom
                },
        )

        // Gestures: tap toggles controls, double tap seeks or pauses, long press gives 2x speed,
        // and swipes/pinch live in a separate pointerInput (detectPlayerGestures).
        if (!inPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val current = component.engine.state.value.positionMs
                                val yFraction = (offset.y / size.height).coerceIn(0f, 1f)
                                when {
                                    offset.x < size.width / 3f -> {
                                        component.engine.seekTo((current - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0))
                                        val prev = doubleTapSeek?.takeIf { !it.forward }
                                        doubleTapSeek = DoubleTapSeek(
                                            forward = false,
                                            totalSec = (prev?.totalSec ?: 0) + DOUBLE_TAP_SEEK_SEC,
                                            key = (doubleTapSeek?.key ?: 0) + 1,
                                            verticalFraction = yFraction,
                                        )
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    offset.x > size.width * 2f / 3f -> {
                                        component.engine.seekTo(current + DOUBLE_TAP_SEEK_MS)
                                        val prev = doubleTapSeek?.takeIf { it.forward }
                                        doubleTapSeek = DoubleTapSeek(
                                            forward = true,
                                            totalSec = (prev?.totalSec ?: 0) + DOUBLE_TAP_SEEK_SEC,
                                            key = (doubleTapSeek?.key ?: 0) + 1,
                                            verticalFraction = yFraction,
                                        )
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    else -> if (state.isPlaying) component.engine.pause() else component.engine.play()
                                }
                            },
                            onLongPress = {
                                if (!speedBoost) speedBeforeBoost = component.engine.state.value.speed
                                speedBoost = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                component.engine.setSpeed(2f)
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (speedBoost) {
                                    speedBoost = false
                                    component.engine.setSpeed(speedBeforeBoost)
                                }
                            },
                        )
                    }
                    .pointerInput(gesturesEnabled) {
                        if (!gesturesEnabled) return@pointerInput

                        var seekSession: SeekSwipeSession? = null
                        var seekStartMs = 0L
                        var seekTargetMs = 0L
                        var levelSession: LevelSwipeSession? = null
                        var levelControl = LevelControl.BRIGHTNESS
                        var pinchSession: PinchZoomSession? = null

                        detectPlayerGestures(object : PlayerGestureCallbacks {
                            override fun onSeekStart() {
                                gestureActive = true
                                val current = component.engine.state.value
                                seekStartMs = current.positionMs
                                seekTargetMs = current.positionMs
                                seekSession = SeekSwipeSession(
                                    startPositionMs = current.positionMs,
                                    durationMs = current.durationMs,
                                    widthPx = size.width.toFloat(),
                                    sensitivity = gestureSensitivity,
                                )
                            }

                            override fun onSeekDrag(totalDxPx: Float) {
                                val session = seekSession ?: return
                                seekTargetMs = session.positionFor(totalDxPx)
                                indicator = GestureIndicator.Seek(seekTargetMs, seekTargetMs - seekStartMs)
                            }

                            override fun onSeekEnd() {
                                if (seekSession != null) component.engine.seekTo(seekTargetMs)
                                seekSession = null
                                gestureActive = false
                                lingerKey++
                            }

                            override fun onLevelStart(leftSide: Boolean) {
                                gestureActive = true
                                levelControl = if (leftSide) LevelControl.BRIGHTNESS else LevelControl.VOLUME
                                val start = when (levelControl) {
                                    LevelControl.BRIGHTNESS -> systemControls.brightness
                                    LevelControl.VOLUME -> systemControls.volume
                                }
                                levelSession = LevelSwipeSession(start, size.height.toFloat(), gestureSensitivity)
                            }

                            override fun onLevelDrag(totalDyPx: Float) {
                                val session = levelSession ?: return
                                val level = session.levelFor(totalDyPx)
                                when (levelControl) {
                                    LevelControl.BRIGHTNESS -> systemControls.setBrightness(level)
                                    LevelControl.VOLUME -> systemControls.setVolume(level)
                                }
                                indicator = GestureIndicator.Level(levelControl, level)
                            }

                            override fun onLevelEnd() {
                                levelSession = null
                                gestureActive = false
                                lingerKey++
                            }

                            override fun onPinch(cumulativeZoom: Float) {
                                gestureActive = true
                                val videoState = component.engine.state.value
                                val fill = fillFactorFor(
                                    videoState.videoWidth,
                                    videoState.videoHeight,
                                    size.width,
                                    size.height,
                                )
                                val session = pinchSession
                                    ?: PinchZoomSession(scaleMode, manualZoom).also { pinchSession = it }
                                val next = session.update(cumulativeZoom)
                                if (next.mode != scaleMode) {
                                    // Haptic feedback when switching the standard fit/fill crop.
                                    scaleMode = next.mode
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (next.manual != manualZoom) manualZoom = next.manual
                                indicator = GestureIndicator.Zoom(next.mode, next.manual, fill * next.manual)
                            }

                            override fun onPinchEnd() {
                                pinchSession = null
                                gestureActive = false
                                lingerKey++
                            }
                        })
                    },
            )
        }

        AnimatedVisibility(
            visible = speedBoost && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 48.dp),
        ) {
            Badge("2×")
        }

        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControls(
                component = component,
                onPresetClick = {
                    speedSheetVisible = false
                    presetSheetVisible = true
                },
                onSpeedClick = {
                    presetSheetVisible = false
                    speedSheetVisible = true
                },
            )
        }

        // Compact feedback sits top-center; level swipes use side rails near the gesture.
        if (!inPip) {
            indicator?.let { current ->
                when (current) {
                    is GestureIndicator.Level -> LevelIndicatorRail(
                        indicator = current,
                        modifier = Modifier
                            .align(
                                when (current.control) {
                                    LevelControl.BRIGHTNESS -> Alignment.CenterStart
                                    LevelControl.VOLUME -> Alignment.CenterEnd
                                },
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 18.dp),
                    )

                    is GestureIndicator.Top -> TopGestureIndicatorBadge(
                        current,
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(top = 48.dp),
                    )
                }
            }
        }

        // YouTube-like double-tap feedback: side lens with ripples.
        if (!inPip) {
            doubleTapSeek?.let { seek ->
                YouTubeSeekOverlay(
                    forward = seek.forward,
                    seconds = seek.totalSec,
                    rippleKey = seek.key,
                    verticalFraction = seek.verticalFraction,
                    modifier = Modifier
                        .align(if (seek.forward) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxWidth(0.45f),
                )
            }
        }

        // Buffering without controls shows the brand loader in the center.
        if (state.status == PlaybackStatus.BUFFERING && !controlsVisible && !inPip) {
            EclipseLoader(Modifier.align(Alignment.Center))
        }

        if (debugEnabled && !inPip) {
            DebugOverlay(
                component = component,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 12.dp, top = 64.dp),
            )
        }

        AnimatedVisibility(
            visible = presetSheetVisible && !inPip,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PresetSheet(
                component = component,
                activePresetId = selectedPresetId,
                onDismiss = { presetSheetVisible = false },
            )
        }

        AnimatedVisibility(
            visible = speedSheetVisible && !inPip,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SpeedSheet(
                currentSpeed = state.speed,
                onSpeedSelected = { speed ->
                    component.engine.setSpeed(speed)
                    speedSheetVisible = false
                },
            )
        }
    }
}

@Composable
private fun PlayerControls(
    component: PlayerComponent,
    onPresetClick: () -> Unit,
    onSpeedClick: () -> Unit,
) {
    val state by component.engine.state.collectAsState()
    val activePreset = BuiltInPresets.byId(state.activeUpscaleProfileId) ?: BuiltInPresets.OFF
    val upscaleActive = activePreset.isEnabled

    Box(Modifier.fillMaxSize()) {
        // Gradient scrims keep the frame center bright.
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(SakuroColors.Background.copy(alpha = 0.82f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, SakuroColors.Background.copy(alpha = 0.88f)),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Top bar: back, title, preset, debug.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = component::onBack) {
                    Icon(Lucide.ChevronLeft, stringResource(Res.string.action_back), tint = SakuroColors.TextPrimary)
                }
                Text(
                    text = component.media.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SakuroColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onSpeedClick,
                    shape = RoundedCornerShape(50),
                    color = SakuroColors.Surface.copy(alpha = 0.6f),
                    contentColor = if (abs(state.speed - 1f) < 0.01f) {
                        SakuroColors.TextMuted
                    } else {
                        SakuroColors.AccentSakura
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = formatPlaybackSpeed(state.speed),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                // Upscale-on indicator.
                Surface(
                    onClick = onPresetClick,
                    shape = RoundedCornerShape(50),
                    color = if (upscaleActive) {
                        SakuroColors.GlowMagenta.copy(alpha = 0.35f)
                    } else {
                        SakuroColors.Surface.copy(alpha = 0.6f)
                    },
                    contentColor = if (upscaleActive) SakuroColors.AccentSakura else SakuroColors.TextMuted,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Lucide.Sparkles, null, Modifier.size(14.dp))
                        Text(activePreset.displayName(), fontSize = 12.sp)
                    }
                }
                IconButton(onClick = component::toggleDebugOverlay) {
                    Icon(
                        Lucide.Activity,
                        stringResource(Res.string.player_stats_for_nerds),
                        tint = SakuroColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Center transport controls.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(
                    icon = Lucide.RotateCcw,
                    contentDescription = stringResource(Res.string.player_seek_back),
                    onClick = { component.engine.seekTo((state.positionMs - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0)) },
                )
                Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                    if (state.status == PlaybackStatus.BUFFERING) {
                        EclipseLoader(size = 60.dp)
                    } else {
                        PlayPauseButton(
                            isPlaying = state.isPlaying,
                            onClick = { if (state.isPlaying) component.engine.pause() else component.engine.play() },
                        )
                    }
                }
                SkipButton(
                    icon = Lucide.RotateCw,
                    contentDescription = stringResource(Res.string.player_seek_forward),
                    onClick = { component.engine.seekTo(state.positionMs + DOUBLE_TAP_SEEK_MS) },
                )
            }

            Spacer(Modifier.weight(1f))

            // Bottom bar: progress and time.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SakuroSeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    bufferedMs = state.bufferedMs,
                    onSeek = { component.engine.seekTo(it) },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TimeText(formatTime(state.positionMs))
                    Spacer(Modifier.weight(1f))
                    TimeText(formatTime(state.durationMs))
                }
            }
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = SakuroColors.TextPrimary,
    )
}

@Composable
private fun TopGestureIndicatorBadge(indicator: GestureIndicator.Top, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SakuroColors.Background.copy(alpha = 0.75f),
        contentColor = SakuroColors.TextPrimary,
        modifier = modifier,
    ) {
        when (indicator) {
            is GestureIndicator.Seek -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(formatTime(indicator.targetMs), fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                Text(
                    text = (if (indicator.deltaMs >= 0) "+" else "−") + formatTime(abs(indicator.deltaMs)),
                    fontSize = 13.sp,
                    color = SakuroColors.AccentSakura,
                )
            }

            is GestureIndicator.Zoom -> {
                val isFit = indicator.mode == ScaleMode.FIT
                val isFill = !isFit && indicator.manual <= 1.01f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = if (isFit) Lucide.Minimize else Lucide.Maximize,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = SakuroColors.AccentSakura,
                    )
                    Text(
                        text = when {
                            isFit -> stringResource(Res.string.scale_fit_to_screen)
                            isFill -> stringResource(Res.string.scale_fill_screen)
                            else -> formatZoom(indicator.totalFactor)
                        },
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelIndicatorRail(indicator: GestureIndicator.Level, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SakuroColors.Background.copy(alpha = 0.72f),
        contentColor = SakuroColors.TextPrimary,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Icon(
                when (indicator.control) {
                    LevelControl.BRIGHTNESS -> Lucide.Sun
                    LevelControl.VOLUME -> Lucide.Volume2
                },
                null,
                Modifier.size(18.dp),
                tint = SakuroColors.AccentSakura,
            )
            Box(
                modifier = Modifier
                    .height(150.dp)
                    .width(8.dp)
                    .background(SakuroColors.Twilight.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(indicator.value)
                        .background(SakuroColors.AccentSakura, CircleShape),
                )
            }
            Text(
                text = "${(indicator.value * 100).toInt()}%",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SakuroColors.Background.copy(alpha = 0.7f),
        contentColor = SakuroColors.AccentSakura,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 14.sp)
    }
}

@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    Surface(
        color = SakuroColors.SurfaceElevated,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 12.dp),
        ) {
            Text(
                stringResource(Res.string.player_playback_speed),
                style = MaterialTheme.typography.titleMedium,
                color = SakuroColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                val selected = abs(speed - currentSpeed) < 0.01f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSpeedSelected(speed) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatPlaybackSpeed(speed),
                        color = SakuroColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Lucide.Check, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSheet(
    component: PlayerComponent,
    activePresetId: String?,
    onDismiss: () -> Unit,
) {
    val presets by component.presets.collectAsState()
    Surface(
        color = SakuroColors.SurfaceElevated,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(Res.string.player_upscale_preset),
                style = MaterialTheme.typography.titleMedium,
                color = SakuroColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            presets.forEach { preset ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            component.applyPreset(preset.id)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            preset.displayName(),
                            color = SakuroColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            preset.displayDescription(),
                            color = SakuroColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preset.id == activePresetId) {
                        Icon(Lucide.Check, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
                    }
                }
            }
            PinRow(component)
        }
    }
}

/** Preset pinning for a file: the pin has priority over the global default and Auto. */
@Composable
private fun PinRow(component: PlayerComponent) {
    val isPinned by component.isPinned.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { component.togglePinned() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Lucide.Pin,
            null,
            tint = if (isPinned) SakuroColors.AccentSakura else SakuroColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.player_pin_title),
                color = SakuroColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.player_pin_desc),
                color = SakuroColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = isPinned,
            onCheckedChange = { component.togglePinned() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SakuroColors.AccentSakura,
                checkedTrackColor = SakuroColors.GlowMagenta.copy(alpha = 0.5f),
            ),
        )
    }
}
