package com.rinwave.sakuro.engine.media3

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The player the app currently plays on screen. The engine owns the player (it needs
 * ExoPlayer-only APIs such as video effects), so it publishes it here and the
 * [SakuroPlaybackService] wraps whatever is published in a [MediaSession].
 */
internal object PlaybackSessionRegistry {
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    /**
     * Whether the app's UI is hidden while playback continues. Kept here, not only per engine,
     * because autoplay can create the next engine while the app is still in the background
     * and that engine never sees the UI "stop".
     */
    @Volatile
    var uiInBackground: Boolean = false

    fun publish(player: Player) {
        _player.value = player
    }

    /** Only the current owner can withdraw: a replaced engine releases after its successor publishes. */
    fun withdraw(player: Player) {
        _player.compareAndSet(player, null)
    }
}

/**
 * Media3's recommended background-playback host. Lifecycle:
 *
 * 1. The engine publishes its player and connects a [MediaController] ([connect]); binding
 *    creates this service and [onGetSession] wraps the player in a session.
 * 2. While the player plays, [MediaSessionService] promotes itself to a foreground service with
 *    the default media notification (play/pause, seek, title); pausing demotes it so the user
 *    can swipe the notification away.
 * 3. A new video replaces the session; the engine's release withdraws the player, the session
 *    is released and the service stops itself once no controller is bound.
 * 4. Swiping the app away from recents pauses and stops everything ([onTaskRemoved]).
 */
@OptIn(UnstableApi::class)
class SakuroPlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var sessionCounter = 0

    override fun onCreate() {
        super.onCreate()
        scope.launch { PlaybackSessionRegistry.player.collect(::bind) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session ?: PlaybackSessionRegistry.player.value?.let { player ->
            bind(player)
            session
        }

    private fun bind(player: Player?) {
        val current = session
        if (current != null && current.player === player) return
        current?.let {
            removeSession(it)
            it.release()
        }
        session = player?.let {
            MediaSession.Builder(this, it)
                // Unique ids: the previous session may still be tearing down in a controller.
                .setId("sakuro-${++sessionCounter}")
                .apply { launchActivityIntent()?.let(::setSessionActivity) }
                .build()
                .also(::addSession)
        }
        if (session == null) stopSelf()
    }

    /** Tapping the notification returns to the running player screen (the activity is singleTask). */
    private fun launchActivityIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }

    internal companion object {
        /**
         * Binds a controller to the service, which is how Media3 expects a session service to be
         * started from the app (it then manages foreground state on its own).
         */
        fun connect(context: Context): ListenableFuture<MediaController> =
            MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, SakuroPlaybackService::class.java)),
            ).buildAsync()
    }
}
