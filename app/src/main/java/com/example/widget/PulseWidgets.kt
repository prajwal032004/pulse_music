package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.PulseApplication
import com.example.R
import com.example.model.PlaybackState
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.playback.PulseMediaSessionService
import com.example.util.ArtworkBitmaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 4×1 compact Now Playing widget. */
class NowPlayingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        (context.applicationContext as PulseApplication).appContainer.widgetUpdater.requestUpdate()
    }
}

/** 4×3 Music Dashboard widget. */
class DashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        (context.applicationContext as PulseApplication).appContainer.widgetUpdater.requestUpdate()
    }
}

/**
 * Handles taps on widget buttons. Runs in the app process, so it drives PlaybackManager directly;
 * when playback starts while the media service isn't running, the service is started in the
 * foreground (allowed because the user interacted with a widget).
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PulseApplication).appContainer
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                handle(context, container, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, container: AppContainer, intent: Intent) {
        val pm = container.playbackManager
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (!pm.ensureQueueLoaded()) return
                pm.togglePlayPause()
            }
            ACTION_NEXT -> if (pm.ensureQueueLoaded()) pm.seekToNext()
            ACTION_PREVIOUS -> if (pm.ensureQueueLoaded()) pm.seekToPrevious()
            ACTION_SHUFFLE -> {
                val songs = container.repository.libraryData.value.songs
                if (songs.isNotEmpty()) {
                    if (pm.playbackState.value.shuffleMode == ShuffleMode.OFF) pm.toggleShuffle()
                    pm.playQueue(songs.shuffled(), 0)
                }
            }
            ACTION_FAVORITE -> {
                val song = pm.playbackState.value.currentSong ?: return
                container.repository.toggleFavorite(song.id)
            }
            ACTION_PLAY_SONG -> {
                val id = intent.getLongExtra(EXTRA_SONG_ID, Long.MIN_VALUE)
                val songs = container.repository.libraryData.value.songs
                val index = songs.indexOfFirst { it.id == id }
                if (index >= 0) pm.playQueue(songs, index)
            }
            ACTION_PLAY_MIX -> {
                val engine = container.smartEngine
                val mix = when (intent.getStringExtra(EXTRA_MIX)) {
                    MIX_LIKED -> container.repository.libraryData.value.songs.filter { it.isFavorite }
                    MIX_CHILL -> engine.getChillEveningMix()
                    MIX_WORKOUT -> engine.getWorkoutMix()
                    MIX_FOCUS -> engine.getFocusFlowMix()
                    else -> engine.getSurpriseMeMix()
                }
                if (mix.isNotEmpty()) pm.playQueue(mix, 0)
            }
        }
        if (pm.exoPlayer.playWhenReady && !PulseMediaSessionService.isRunning) {
            ContextCompat.startForegroundService(context, Intent(context, PulseMediaSessionService::class.java))
        }
        container.widgetUpdater.requestUpdate()
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.pulse.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.pulse.widget.NEXT"
        const val ACTION_PREVIOUS = "com.example.pulse.widget.PREVIOUS"
        const val ACTION_SHUFFLE = "com.example.pulse.widget.SHUFFLE"
        const val ACTION_FAVORITE = "com.example.pulse.widget.FAVORITE"
        const val ACTION_PLAY_SONG = "com.example.pulse.widget.PLAY_SONG"
        const val ACTION_PLAY_MIX = "com.example.pulse.widget.PLAY_MIX"
        const val EXTRA_SONG_ID = "song_id"
        const val EXTRA_MIX = "mix"
        const val MIX_LIKED = "liked"
        const val MIX_CHILL = "chill"
        const val MIX_WORKOUT = "workout"
        const val MIX_FOCUS = "focus"
    }
}

/** Pushes playback state to every placed widget; progress refreshes every few seconds while playing. */
@kotlin.OptIn(FlowPreview::class)
class WidgetUpdater(private val context: Context, private val container: AppContainer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manualRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var cachedArtKey: String? = null
    private var cachedArt: Bitmap? = null

    private data class Snapshot(val songId: Long?, val playing: Boolean, val favorite: Boolean, val shuffle: Boolean, val queueSize: Int)

    fun start() {
        scope.launch {
            combine(
                container.playbackManager.playbackState
                    .map { Snapshot(it.currentSong?.id, it.isPlaying, it.currentSong?.isFavorite == true, it.shuffleMode == ShuffleMode.ON, it.queue.size) }
                    .distinctUntilChanged(),
                manualRequests.onStart { emit(Unit) }
            ) { snapshot, _ -> snapshot }
                .debounce(150)
                .collectLatest {
                    if (!hasWidgets()) return@collectLatest
                    pushFullUpdate(container.playbackManager.playbackState.value)
                    // Keep the progress bar moving while playing.
                    while (isActive && container.playbackManager.playbackState.value.isPlaying) {
                        delay(3_000)
                        pushProgress(container.playbackManager.playbackState.value)
                    }
                }
        }
    }

    fun requestUpdate() {
        manualRequests.tryEmit(Unit)
    }

    private fun ids(cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    private fun hasWidgets() =
        ids(NowPlayingWidgetProvider::class.java).isNotEmpty() || ids(DashboardWidgetProvider::class.java).isNotEmpty()

    private fun progressOf(state: PlaybackState): Int =
        if (state.durationMs > 0) ((state.currentPositionMs * 1000) / state.durationMs).toInt().coerceIn(0, 1000) else 0

    private suspend fun artworkFor(song: Song?): Bitmap = withContext(Dispatchers.IO) {
        val key = song?.let { "${it.id}" } ?: "empty"
        if (key == cachedArtKey) cachedArt?.let { return@withContext it }
        val bmp = if (song == null) ArtworkBitmaps.generated("Pulse Music", 256)
        else ArtworkBitmaps.artworkOrGenerated(context, song, 256)
        val rounded = ArtworkBitmaps.rounded(bmp, 36f)
        cachedArtKey = key
        cachedArt = rounded
        rounded
    }

    private suspend fun pushFullUpdate(state: PlaybackState) {
        val mgr = AppWidgetManager.getInstance(context)
        val art = artworkFor(state.currentSong)
        val compactIds = ids(NowPlayingWidgetProvider::class.java)
        if (compactIds.isNotEmpty()) {
            mgr.updateAppWidget(compactIds, buildCompact(state, art))
        }
        val dashIds = ids(DashboardWidgetProvider::class.java)
        if (dashIds.isNotEmpty()) {
            val recent = recentSongs(state)
            val thumbs = withContext(Dispatchers.IO) {
                recent.map { ArtworkBitmaps.rounded(ArtworkBitmaps.artworkOrGenerated(context, it, 144), 28f) }
            }
            mgr.updateAppWidget(dashIds, buildDashboard(state, art, recent, thumbs))
        }
    }

    private fun pushProgress(state: PlaybackState) {
        val mgr = AppWidgetManager.getInstance(context)
        val progress = progressOf(state)
        ids(NowPlayingWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let {
            val rv = RemoteViews(context.packageName, R.layout.widget_now_playing)
            rv.setProgressBar(R.id.widget_progress, 1000, progress, false)
            mgr.partiallyUpdateAppWidget(it, rv)
        }
        ids(DashboardWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let {
            val rv = RemoteViews(context.packageName, R.layout.widget_dashboard)
            rv.setProgressBar(R.id.widget_progress, 1000, progress, false)
            mgr.partiallyUpdateAppWidget(it, rv)
        }
    }

    private suspend fun recentSongs(state: PlaybackState): List<Song> {
        val library = container.repository.libraryData.value.songs
        if (library.isEmpty()) return emptyList()
        val byId = library.associateBy { it.id }
        val history = container.repository.recentHistory.firstOrNull().orEmpty()
        val fromHistory = history.map { it.songId }.distinct().mapNotNull { byId[it] }
        val fill = state.queue.drop(state.queueIndex + 1) + library
        return (fromHistory + fill).distinctBy { it.id }.filter { it.id != state.currentSong?.id }.take(5)
    }

    // -----------------------------------------------------------------------------------------
    // RemoteViews builders
    // -----------------------------------------------------------------------------------------

    private fun actionIntent(action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
        }
        return PendingIntent.getActivity(context, 100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun RemoteViews.bindTransport(state: PlaybackState, art: Bitmap) {
        val song = state.currentSong
        setTextViewText(R.id.widget_title, song?.title ?: "Tap play to start")
        setTextViewText(R.id.widget_artist, song?.artist ?: "Pulse Music")
        setImageViewBitmap(R.id.widget_art, art)
        setImageViewResource(R.id.widget_play, if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        setContentDescription(R.id.widget_play, if (state.isPlaying) "Pause" else "Play")
        setProgressBar(R.id.widget_progress, 1000, progressOf(state), false)
        setOnClickPendingIntent(R.id.widget_play, actionIntent(WidgetActionReceiver.ACTION_PLAY_PAUSE, 1))
        setOnClickPendingIntent(R.id.widget_next, actionIntent(WidgetActionReceiver.ACTION_NEXT, 2))
        setOnClickPendingIntent(R.id.widget_prev, actionIntent(WidgetActionReceiver.ACTION_PREVIOUS, 3))
        setOnClickPendingIntent(R.id.widget_art, openAppIntent())
        setOnClickPendingIntent(R.id.widget_info, openAppIntent())
    }

    private fun buildCompact(state: PlaybackState, art: Bitmap): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_now_playing).apply { bindTransport(state, art) }

    private fun buildDashboard(state: PlaybackState, art: Bitmap, recent: List<Song>, thumbs: List<Bitmap>): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dashboard).apply {
            bindTransport(state, art)
            val favorite = state.currentSong?.isFavorite == true
            setImageViewResource(R.id.widget_favorite, if (favorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
            setOnClickPendingIntent(R.id.widget_favorite, actionIntent(WidgetActionReceiver.ACTION_FAVORITE, 4))
            setOnClickPendingIntent(R.id.widget_shuffle, actionIntent(WidgetActionReceiver.ACTION_SHUFFLE, 5))
            setOnClickPendingIntent(R.id.widget_header, openAppIntent())

            val slots = listOf(R.id.widget_recent_0, R.id.widget_recent_1, R.id.widget_recent_2, R.id.widget_recent_3, R.id.widget_recent_4)
            val labels = listOf(R.id.widget_recent_label_0, R.id.widget_recent_label_1, R.id.widget_recent_label_2, R.id.widget_recent_label_3, R.id.widget_recent_label_4)
            slots.forEachIndexed { i, id ->
                val song = recent.getOrNull(i)
                if (song != null) {
                    setImageViewBitmap(id, thumbs[i])
                    setTextViewText(labels[i], song.title)
                    val pi = actionIntent(WidgetActionReceiver.ACTION_PLAY_SONG, 20 + i) { putExtra(WidgetActionReceiver.EXTRA_SONG_ID, song.id) }
                    setOnClickPendingIntent(id, pi)
                    setContentDescription(id, "Play ${song.title}")
                } else {
                    setImageViewBitmap(id, ArtworkBitmaps.rounded(ArtworkBitmaps.generated("slot$i", 96), 20f))
                    setTextViewText(labels[i], "")
                }
            }

            setOnClickPendingIntent(R.id.widget_chip_liked, actionIntent(WidgetActionReceiver.ACTION_PLAY_MIX, 30) { putExtra(WidgetActionReceiver.EXTRA_MIX, WidgetActionReceiver.MIX_LIKED) })
            setOnClickPendingIntent(R.id.widget_chip_chill, actionIntent(WidgetActionReceiver.ACTION_PLAY_MIX, 31) { putExtra(WidgetActionReceiver.EXTRA_MIX, WidgetActionReceiver.MIX_CHILL) })
            setOnClickPendingIntent(R.id.widget_chip_workout, actionIntent(WidgetActionReceiver.ACTION_PLAY_MIX, 32) { putExtra(WidgetActionReceiver.EXTRA_MIX, WidgetActionReceiver.MIX_WORKOUT) })
            setOnClickPendingIntent(R.id.widget_chip_focus, actionIntent(WidgetActionReceiver.ACTION_PLAY_MIX, 33) { putExtra(WidgetActionReceiver.EXTRA_MIX, WidgetActionReceiver.MIX_FOCUS) })
        }
}
