package com.rinwave.sakuro.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player the media session sees. ExoPlayer holds one item, while the folder queue lives
 * in the app's navigation, so this wrapper advertises previous/next and hands those presses
 * to the app. That covers the notification and lock-screen buttons, headset and car keys.
 */
@OptIn(UnstableApi::class)
internal class QueueNavigationPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
    private var onPrevious: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null

    fun setNavigation(previous: (() -> Unit)?, next: (() -> Unit)?) {
        onPrevious = previous
        onNext = next
        invalidateState()
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = state.availableCommands.buildUpon()
            .addIf(Player.COMMAND_SEEK_TO_NEXT, onNext != null)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, onNext != null)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, onPrevious != null)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, onPrevious != null)
            .build()
        return state.buildUpon().setAvailableCommands(commands).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val previous = onPrevious
        val next = onNext
        when {
            next != null && seekCommand in NEXT_COMMANDS -> next()
            previous != null && seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> previous()
            // "Previous" restarts the video first, like every media player, unless near its start.
            previous != null && seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS &&
                player.currentPosition <= player.maxSeekToPreviousPosition -> previous()
            // A single-item timeline has no next/previous item: Media3 passes an unset index.
            mediaItemIndex == C.INDEX_UNSET -> Unit
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val NEXT_COMMANDS = setOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }
}
