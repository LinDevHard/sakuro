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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Volume2
import com.rinwave.sakuro.core.player.PlaybackStatus
import com.rinwave.sakuro.core.upscale.BuiltInPresets
import com.rinwave.sakuro.navigation.PlayerComponent
import com.rinwave.sakuro.ui.PipEffect
import com.rinwave.sakuro.ui.ScaleMode
import com.rinwave.sakuro.ui.VideoSurface
import com.rinwave.sakuro.ui.displayName
import com.rinwave.sakuro.ui.gestures.LevelSwipeSession
import com.rinwave.sakuro.ui.gestures.PinchSession
import com.rinwave.sakuro.ui.gestures.PlayerGestureCallbacks
import com.rinwave.sakuro.ui.gestures.SeekSwipeSession
import com.rinwave.sakuro.ui.gestures.detectPlayerGestures
import com.rinwave.sakuro.ui.rememberIsInPip
import com.rinwave.sakuro.ui.rememberPlayerSystemControls
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatTime
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val CONTROLS_HIDE_DELAY_MS = 3500L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val INDICATOR_LINGER_MS = 600L

/** Что показывает центральный индикатор во время жеста. */
private sealed interface GestureIndicator {
    data class Seek(val targetMs: Long, val deltaMs: Long) : GestureIndicator
    data class Level(val isVolume: Boolean, val value: Float) : GestureIndicator
    data class Frame(val mode: ScaleMode) : GestureIndicator
}

@Composable
fun PlayerScreen(component: PlayerComponent) {
    val state by component.engine.state.collectAsState()
    val debugEnabled by component.debugOverlay.collectAsState()
    val selectedPresetId by component.selectedPresetId.collectAsState()
    val gesturesEnabled by component.gesturesEnabled.collectAsState()
    val gestureSensitivity by component.gestureSensitivity.collectAsState()

    val systemControls = rememberPlayerSystemControls()
    val haptics = LocalHapticFeedback.current

    // PiP при сворачивании (FEATURES.md §3.1); в PiP-окне — только видео.
    PipEffect(state.isPlaying, state.videoWidth, state.videoHeight)
    val inPip = rememberIsInPip()
    var wasInPip by remember { mutableStateOf(inPip) }
    LaunchedEffect(inPip) {
        if (inPip != wasInPip) {
            wasInPip = inPip
            component.onPipModeChanged()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var presetSheetVisible by remember { mutableStateOf(false) }
    var speedBoost by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(ScaleMode.FIT) }

    var indicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    var lingerKey by remember { mutableStateOf(0) }

    // Авто-скрытие контролов при воспроизведении (DESIGN.md §6).
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Индикатор жеста задерживается на экране после отпускания пальца.
    LaunchedEffect(lingerKey) {
        if (lingerKey > 0) {
            delay(INDICATOR_LINGER_MS)
            if (!gestureActive) indicator = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(component.engine, scaleMode, Modifier.fillMaxSize())

        // Жесты (FEATURES.md §3.1): тап — контролы, двойной тап — перемотка/пауза,
        // удержание — ускорение 2× на время удержания; свайпы и пинч —
        // в отдельном pointerInput (detectPlayerGestures).
        if (!inPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                when {
                                    offset.x < size.width / 3f -> component.engine.seekTo(
                                        (state.positionMs - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0),
                                    )

                                    offset.x > size.width * 2f / 3f ->
                                        component.engine.seekTo(state.positionMs + DOUBLE_TAP_SEEK_MS)

                                    else -> if (state.isPlaying) component.engine.pause() else component.engine.play()
                                }
                            },
                            onLongPress = {
                                speedBoost = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                component.engine.setSpeed(2f)
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (speedBoost) {
                                    speedBoost = false
                                    component.engine.setSpeed(1f)
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
                        var levelIsVolume = false
                        var pinchSession: PinchSession? = null

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
                                levelIsVolume = !leftSide
                                val start = if (levelIsVolume) systemControls.volume else systemControls.brightness
                                levelSession = LevelSwipeSession(start, size.height.toFloat(), gestureSensitivity)
                            }

                            override fun onLevelDrag(totalDyPx: Float) {
                                val session = levelSession ?: return
                                val level = session.levelFor(totalDyPx)
                                if (levelIsVolume) {
                                    systemControls.setVolume(level)
                                } else {
                                    systemControls.setBrightness(level)
                                }
                                indicator = GestureIndicator.Level(levelIsVolume, level)
                            }

                            override fun onLevelEnd() {
                                levelSession = null
                                gestureActive = false
                                lingerKey++
                            }

                            override fun onPinch(cumulativeZoom: Float) {
                                gestureActive = true
                                val session = pinchSession ?: PinchSession(scaleMode).also { pinchSession = it }
                                val newMode = session.update(cumulativeZoom)
                                if (newMode != scaleMode) {
                                    scaleMode = newMode
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                indicator = GestureIndicator.Frame(newMode)
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
                onPresetClick = { presetSheetVisible = true },
            )
        }

        // Поверх контролов: в центре у них play/pause, бейдж не должен прятаться за ним.
        if (!inPip) {
            indicator?.let { current ->
                GestureIndicatorBadge(current, Modifier.align(Alignment.Center))
            }
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
    }
}

@Composable
private fun PlayerControls(component: PlayerComponent, onPresetClick: () -> Unit) {
    val state by component.engine.state.collectAsState()
    val activePreset = BuiltInPresets.byId(state.activeUpscaleProfileId) ?: BuiltInPresets.OFF
    val upscaleActive = activePreset.isEnabled

    Box(
        Modifier
            .fillMaxSize()
            .background(SakuroColors.Background.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Верхняя панель: назад, название, пресет, debug.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = component::onBack) {
                    Icon(Lucide.ChevronLeft, "Назад", tint = SakuroColors.TextPrimary)
                }
                Text(
                    text = component.media.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SakuroColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Индикатор «апскейл включён» (DESIGN.md §5).
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
                        Text(activePreset.name, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = component::toggleDebugOverlay) {
                    Icon(
                        Lucide.Activity,
                        "Stats for nerds",
                        tint = SakuroColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Центральный транспорт.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { component.engine.seekTo((state.positionMs - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0)) },
                ) {
                    Icon(Lucide.RotateCcw, "-10 сек", tint = SakuroColors.TextPrimary, modifier = Modifier.size(30.dp))
                }
                Surface(
                    onClick = { if (state.isPlaying) component.engine.pause() else component.engine.play() },
                    shape = CircleShape,
                    color = SakuroColors.AccentSakura,
                    contentColor = SakuroColors.Background,
                ) {
                    Icon(
                        if (state.isPlaying) Lucide.Pause else Lucide.Play,
                        if (state.isPlaying) "Пауза" else "Играть",
                        modifier = Modifier.padding(18.dp).size(30.dp),
                    )
                }
                IconButton(onClick = { component.engine.seekTo(state.positionMs + DOUBLE_TAP_SEEK_MS) }) {
                    Icon(Lucide.RotateCw, "+10 сек", tint = SakuroColors.TextPrimary, modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Нижняя панель: прогресс и время.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                var dragPosition by remember { mutableStateOf<Float?>(null) }
                val sliderValue = dragPosition
                    ?: if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                Slider(
                    value = sliderValue,
                    onValueChange = { dragPosition = it },
                    onValueChangeFinished = {
                        dragPosition?.let { component.engine.seekTo((it * state.durationMs).toLong()) }
                        dragPosition = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = SakuroColors.AccentSakura,
                        activeTrackColor = SakuroColors.GlowMagenta,
                        inactiveTrackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
                    ),
                )
                Row(Modifier.fillMaxWidth()) {
                    TimeText(formatTime(state.positionMs))
                    Spacer(Modifier.weight(1f))
                    if (state.status == PlaybackStatus.BUFFERING) {
                        Text("буферизация…", fontSize = 11.sp, color = SakuroColors.TextMuted)
                        Spacer(Modifier.weight(1f))
                    }
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
private fun GestureIndicatorBadge(indicator: GestureIndicator, modifier: Modifier = Modifier) {
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

            is GestureIndicator.Level -> LevelIndicatorContent(
                icon = if (indicator.isVolume) Lucide.Volume2 else Lucide.Sun,
                value = indicator.value,
            )

            is GestureIndicator.Frame -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Lucide.Maximize, null, Modifier.size(18.dp), tint = SakuroColors.AccentSakura)
                Text(indicator.mode.displayName, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LevelIndicatorContent(icon: ImageVector, value: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = SakuroColors.AccentSakura)
        LinearProgressIndicator(
            progress = { value },
            color = SakuroColors.AccentSakura,
            trackColor = SakuroColors.Twilight.copy(alpha = 0.5f),
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = "${(value * 100).toInt()}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
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
                "Пресет апскейла",
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
                        Text(preset.name, color = SakuroColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            preset.description,
                            color = SakuroColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preset.id == activePresetId) {
                        Icon(Lucide.Check, null, tint = SakuroColors.AccentSakura, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
