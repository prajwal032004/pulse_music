package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.PulseApplication
import com.example.R
import com.example.model.ShuffleMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the MediaSession that drives the media notification, lock screen, quick-settings media
 * controls, Bluetooth/wired headset buttons and playback resumption after the app is closed.
 */
@OptIn(UnstableApi::class)
class PulseMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val container get() = (application as PulseApplication).appContainer

    // Wired-headset multi-click detection: 1× play/pause, 2× next, 3× previous.
    private val clickHandler = Handler(Looper.getMainLooper())
    private var clickCount = 0
    private val clickRunnable = Runnable {
        val pm = container.playbackManager
        when (clickCount) {
            1 -> pm.togglePlayPause()
            2 -> pm.seekToNext()
            else -> pm.seekToPrevious()
        }
        clickCount = 0
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_pulse)
        setMediaNotificationProvider(provider)

        val player = container.playbackManager.exoPlayer
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(nowPlayingIntent())
            .setCallback(SessionCallback())
            .setCustomLayout(buildCustomLayout())
            .build()
        mediaSession = session
        // Register immediately so the notification is posted even when the service was started
        // (e.g. from a widget) rather than bound by a controller.
        addSession(session)

        // Keep the heart / shuffle buttons in the notification in sync with app state.
        scope.launch {
            container.playbackManager.playbackState
                .map { (it.currentSong?.isFavorite == true) to (it.shuffleMode == ShuffleMode.ON) }
                .distinctUntilChanged()
                .collect { session.setCustomLayout(buildCustomLayout()) }
        }
    }

    private fun nowPlayingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val state = container.playbackManager.playbackState.value
        val favorite = state.currentSong?.isFavorite == true
        val shuffle = state.shuffleMode == ShuffleMode.ON
        return listOf(
            CommandButton.Builder(if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(if (favorite) "Remove from Liked" else "Like")
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(if (shuffle) "Shuffle on" else "Shuffle off")
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
        )
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(buildCustomLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val pm = container.playbackManager
            when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    val song = pm.playbackState.value.currentSong
                    if (song != null) scope.launch { container.repository.toggleFavorite(song.id) }
                }
                ACTION_TOGGLE_SHUFFLE -> pm.toggleShuffle()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Lets "play" on headphones or the system media card restart the last session after the app was closed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val items = container.playbackManager.buildResumptionItems()
                    ?: container.playbackManager.buildSavedSessionItems()
                if (items != null) {
                    future.set(MediaSession.MediaItemsWithStartPosition(items.first, items.second, items.third))
                } else {
                    future.setException(UnsupportedOperationException("Nothing to resume"))
                }
            }
            return future
        }

        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
            val event: KeyEvent = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }) ?: return false
            if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                clickCount++
                clickHandler.removeCallbacks(clickRunnable)
                if (clickCount >= 3) clickRunnable.run() else clickHandler.postDelayed(clickRunnable, MULTI_CLICK_WINDOW_MS)
            }
            return true
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        clickHandler.removeCallbacks(clickRunnable)
        scope.cancel()
        // The player is an app-wide singleton owned by PlaybackManager, so only the session is released here.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pulse_music_playback_channel"
        const val ACTION_TOGGLE_FAVORITE = "com.example.pulse.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.example.pulse.TOGGLE_SHUFFLE"
        private const val MULTI_CLICK_WINDOW_MS = 420L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
