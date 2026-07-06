package com.rinwave.sakuro.ui

import androidx.compose.runtime.Composable

/**
 * Picture-in-Picture (FEATURES.md §3.1 "swipe down — PiP/minimize"):
 * on Android the player goes into PiP when the app is minimized during
 * playback (auto-enter on 12+, onUserLeaveHint earlier); swipe down
 * is taken by the brightness/volume gesture. Desktop — no-op.
 */

/**
 * While the composition is active, reports the player state for PiP to the platform:
 * whether video is playing (we enter PiP only during playback) and its size
 * (the PiP window aspect). Outside the player screen, PiP is not enabled.
 */
@Composable
expect fun PipEffect(
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
)

/** Whether the activity is in PiP mode now (in PiP we draw video only). */
@Composable
expect fun rememberIsInPip(): Boolean

/**
 * A PiP-state snapshot outside the composition — for lifecycle callbacks
 * (onPause when entering PiP must not stop playback;
 * the system sends onPictureInPictureModeChanged before onPause).
 */
expect fun isInPipNow(): Boolean
