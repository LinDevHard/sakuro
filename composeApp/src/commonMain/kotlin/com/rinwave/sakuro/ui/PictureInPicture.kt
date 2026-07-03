package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * Picture-in-Picture (FEATURES.md §3.1 «свайп вниз — PiP/сворачивание»):
 * на Android плеер уходит в PiP при сворачивании приложения во время
 * воспроизведения (auto-enter на 12+, onUserLeaveHint раньше); свайп вниз
 * занят жестом яркости/громкости. Desktop — no-op.
 */

/**
 * Пока композиция активна, сообщает платформе состояние плеера для PiP:
 * играет ли видео (входим в PiP только при воспроизведении) и его размеры
 * (аспект PiP-окна). Вне экрана плеера PiP не включается.
 */
@Composable
expect fun PipEffect(isPlaying: Boolean, videoWidth: Int, videoHeight: Int)

/** Находится ли активити в PiP-режиме сейчас (в PiP рисуем только видео). */
@Composable
expect fun rememberIsInPip(): Boolean

/**
 * Снимок PiP-состояния вне композиции — для lifecycle-колбэков
 * (onPause при входе в PiP не должен останавливать воспроизведение;
 * система шлёт onPictureInPictureModeChanged до onPause).
 */
expect fun isInPipNow(): Boolean
