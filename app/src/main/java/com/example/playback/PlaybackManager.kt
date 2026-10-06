package com.example.playback

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.audiolab.AudioDeviceMonitor
import com.example.audiolab.AudioLabManager
import com.example.audiolab.DeviceEvent
import com.example.audiolab.EarControlAudioProcessor
import com.example.data.AppSettings
import com.example.data.MusicRepository
import com.example.data.PreferencesManager
import com.example.model.*
import com.example.util.ArtworkBitmaps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(UnstableApi::class)
@kotlin.OptIn(FlowPreview::class)
class PlaybackManager(
    private val context: Context,
    private val audioLabManager: AudioLabManager,
    private val repository: MusicRepository,
    private val preferencesManager: PreferencesManager,
    private val deviceMonitor: AudioDeviceMonitor,
    earProcessor: EarControlAudioProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(earProcessor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(buildAudioAttributes(spatial = true), true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /** Short user-facing messages (e.g. "Resumed on Galaxy Buds") surfaced as in-app toasts. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var settings = AppSettings()
    private var sleepTimerJob: Job? = null
    private var lastPausedAt = 0L
    private var pausedByDisconnectAt = 0L

    // Listening-time tracking for stats
    private var listenedMs = 0L
    private var historyRowId: Long? = null

    init {
        exoPlayer.addListener(createPlayerListener())
        startProgressTracker()
        audioLabManager.attachAudioSession(exoPlayer.audioSessionId)

        scope.launch {
            preferencesManager.settings.collect { s ->
                settings = s
                exoPlayer.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
                if (exoPlayer.skipSilenceEnabled != s.skipSilence) exoPlayer.skipSilenceEnabled = s.skipSilence
                _playbackState.update { it.copy(isSkipSilenceEnabled = s.skipSilence) }
            }
        }
        scope.launch {
            audioLabManager.audioLabState.map { it.spatialAudioEnabled }.distinctUntilChanged().collect { on ->
                exoPlayer.setAudioAttributes(buildAudioAttributes(on), true)
            }
        }
        scope.launch { deviceMonitor.events.collect { onDeviceEvent(it) } }
        scope.launch {
            val speed = preferencesManager.playbackSpeed.first()
            if (speed != 1f) setPlaybackSpeed(speed)
            val pitch = preferencesManager.playbackPitch.first()
            if (pitch != 1f) setPitch(pitch)
            if (preferencesManager.repeatMode.first() == RepeatMode.ALL.name) toggleRepeat()
        }
        scope.launch {
            _playbackState.map { s -> s.queue.map { it.id } }
                .distinctUntilChanged()
                .debounce(800)
                .collect { ids -> if (ids.isNotEmpty()) preferencesManager.saveQueue(ids) }
        }
    }

    private fun buildAudioAttributes(spatial: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .setSpatializationBehavior(if (spatial) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER)
        .build()

    // -----------------------------------------------------------------------------------------
    // Player listener: the player is the source of truth; our state mirrors it so that commands
    // from the notification, lock screen, widgets, Bluetooth buttons and Android Auto stay in sync.
    // -----------------------------------------------------------------------------------------

    private fun createPlayerListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying, currentPositionMs = exoPlayer.currentPosition) }
            audioLabManager.setPlaying(isPlaying)
            if (isPlaying) {
                audioLabManager.attachAudioSession(exoPlayer.audioSessionId)
            } else {
                lastPausedAt = SystemClock.elapsedRealtime()
                persistPosition()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) syncQueueFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            finishHistoryEntry()
            // An A-B loop belongs to the track it was set on.
            if (_playbackState.value.loopPointA != null) _playbackState.update { it.copy(loopPointA = null, loopPointB = null) }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _playbackState.value.sleepAtEndOfTrack) {
                exoPlayer.pause()
                _playbackState.update { it.copy(sleepAtEndOfTrack = false) }
            }
            syncCurrentFromPlayer()
            _playbackState.value.currentSong?.let { song ->
                audioLabManager.autoApplyPresetForGenre(song.genre)
                scope.launch(Dispatchers.IO) { preferencesManager.savePlaybackState(song.id, 0L) }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val mode = if (shuffleModeEnabled) ShuffleMode.ON else ShuffleMode.OFF
            _playbackState.update { it.copy(shuffleMode = mode) }
            syncCurrentFromPlayer()
            scope.launch { preferencesManager.saveShuffleMode(mode.name) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            _playbackState.update { it.copy(repeatMode = mode) }
            scope.launch { preferencesManager.saveRepeatMode(mode.name) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackState.update { it.copy(speed = playbackParameters.speed, pitch = playbackParameters.pitch) }
        }
    }

    private fun songLookup(): Map<Long, Song> {
        val map = HashMap<Long, Song>()
        repository.libraryData.value.songs.forEach { map[it.id] = it }
        _playbackState.value.queue.forEach { map.putIfAbsent(it.id, it) }
        return map
    }

    private fun syncQueueFromPlayer() {
        val lookup = songLookup()
        val queue = (0 until exoPlayer.mediaItemCount).map { i ->
            val item = exoPlayer.getMediaItemAt(i)
            lookup[item.mediaId.toLongOrNull()] ?: songFromMediaItem(item)
        }
        _playbackState.update { it.copy(queue = queue) }
        syncCurrentFromPlayer()
    }

    private fun syncCurrentFromPlayer() {
        val idx = exoPlayer.currentMediaItemIndex
        val queue = _playbackState.value.queue
        if (exoPlayer.mediaItemCount == 0 || idx !in queue.indices) {
            _playbackState.update { it.copy(currentSong = null, queueIndex = -1, isPlaying = false, currentPositionMs = 0, durationMs = 0) }
            return
        }
        val song = queue[idx]
        val favorite = repository.libraryData.value.songs.firstOrNull { it.id == song.id }?.isFavorite ?: song.isFavorite
        val upNext = buildList {
            val timeline = exoPlayer.currentTimeline
            var next = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, exoPlayer.shuffleModeEnabled)
            while (next != C.INDEX_UNSET && size < 500) {
                add(next)
                next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, exoPlayer.shuffleModeEnabled)
            }
        }
        _playbackState.update {
            it.copy(
                currentSong = song.copy(isFavorite = favorite),
                queueIndex = idx,
                upNextIndices = upNext,
                durationMs = song.durationMs,
                currentPositionMs = exoPlayer.currentPosition
            )
        }
    }

    /** Called when favorites change so the current song's heart stays in sync everywhere. */
    fun refreshCurrentSong() = syncCurrentFromPlayer()

    private fun songFromMediaItem(item: MediaItem): Song {
        val md = item.mediaMetadata
        return Song(
            id = item.mediaId.toLongOrNull() ?: item.mediaId.hashCode().toLong(),
            title = md.title?.toString() ?: "Unknown",
            artist = md.artist?.toString() ?: "Unknown Artist",
            album = md.albumTitle?.toString() ?: "",
            durationMs = 0L,
            uri = item.localConfiguration?.uri ?: android.net.Uri.EMPTY,
            albumArtUri = md.artworkUri
        )
    }

    fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(artist)
            .setGenre(genre)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setReleaseYear(year.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (albumArtUri != null) {
            metadata.setArtworkUri(albumArtUri)
        } else {
            metadata.setArtworkData(ArtworkBitmaps.generatedPng(this, 512), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata.build())
            .build()
    }

    // -----------------------------------------------------------------------------------------
    // Queue control
    // -----------------------------------------------------------------------------------------

    fun playQueue(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (songs.isEmpty()) return
        val clampedIndex = startIndex.coerceIn(0, songs.lastIndex)
        _playbackState.update {
            it.copy(
                queue = songs,
                queueIndex = clampedIndex,
                currentSong = songs[clampedIndex],
                durationMs = songs[clampedIndex].durationMs,
                currentPositionMs = startPositionMs
            )
        }
        exoPlayer.setMediaItems(songs.map { it.toMediaItem() }, clampedIndex, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    fun buildResumptionItems(): Triple<List<MediaItem>, Int, Long>? {
        val state = _playbackState.value
        if (state.queue.isEmpty()) return null
        return Triple(state.queue.map { it.toMediaItem() }, state.queueIndex.coerceAtLeast(0), state.currentPositionMs)
    }

    fun playSong(song: Song) {
        val currentQueue = _playbackState.value.queue
        val existingIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            exoPlayer.seekTo(existingIndex, 0L)
            exoPlayer.play()
        } else {
            playQueue(listOf(song), 0)
        }
    }

    fun playNext(song: Song) {
        if (exoPlayer.mediaItemCount == 0) return playQueue(listOf(song))
        val insertIdx = (exoPlayer.currentMediaItemIndex + 1).coerceAtMost(exoPlayer.mediaItemCount)
        exoPlayer.addMediaItem(insertIdx, song.toMediaItem())
        _messages.tryEmit("\"${song.title}\" will play next")
    }

    fun addToQueue(song: Song) {
        if (exoPlayer.mediaItemCount == 0) return playQueue(listOf(song))
        exoPlayer.addMediaItem(song.toMediaItem())
        _messages.tryEmit("Added \"${song.title}\" to queue")
    }

    fun removeFromQueue(index: Int) {
        if (index in 0 until exoPlayer.mediaItemCount && index != exoPlayer.currentMediaItemIndex) {
            exoPlayer.removeMediaItem(index)
        }
    }

    /** Clears everything after the current track. */
    fun clearUpNext() {
        val current = exoPlayer.currentMediaItemIndex
        val count = exoPlayer.mediaItemCount
        if (count <= 1 || current < 0) return
        if (current + 1 < count) exoPlayer.removeMediaItems(current + 1, count)
        if (current > 0) exoPlayer.removeMediaItems(0, current)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val count = exoPlayer.mediaItemCount
        if (fromIndex in 0 until count && toIndex in 0 until count && fromIndex != toIndex) {
            exoPlayer.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun skipToQueueItem(index: Int) {
        if (index in 0 until exoPlayer.mediaItemCount) {
            exoPlayer.seekTo(index, 0L)
            exoPlayer.play()
        }
    }

    private data class SavedSession(val songs: List<Song>, val index: Int, val positionMs: Long)

    private suspend fun savedSession(library: List<Song>, fallbackToLibrary: Boolean): SavedSession? {
        if (library.isEmpty()) return null
        val byId = library.associateBy { it.id }
        val lastId = preferencesManager.lastSongId.first()
        var position = preferencesManager.lastPositionMs.first()
        var songs = preferencesManager.lastQueueIds().mapNotNull { byId[it] }
        if (songs.isEmpty()) songs = listOfNotNull(lastId?.let { byId[it] })
        if (songs.isEmpty()) {
            if (!fallbackToLibrary) return null
            songs = library.take(100)
            position = 0L
        }
        val index = songs.indexOfFirst { it.id == lastId }.coerceAtLeast(0)
        return SavedSession(songs, index, position)
    }

    private suspend fun awaitLibrary(): List<Song> =
        withTimeoutOrNull(8_000) { repository.libraryData.first { it.songs.isNotEmpty() } }?.songs ?: emptyList()

    /** Restores the last queue (paused) so the mini player, notification and widgets have content. */
    suspend fun restoreLastSession(library: List<Song>): Boolean {
        if (exoPlayer.mediaItemCount > 0) return false
        val session = savedSession(library, fallbackToLibrary = false) ?: return false
        playQueue(session.songs, session.index, session.positionMs, playWhenReady = false)
        return true
    }

    /** Ensures there is something queued (last session, else the library) — used by widgets on a cold start. */
    suspend fun ensureQueueLoaded(): Boolean {
        if (exoPlayer.mediaItemCount > 0) return true
        val session = savedSession(awaitLibrary(), fallbackToLibrary = true) ?: return false
        if (exoPlayer.mediaItemCount > 0) return true
        playQueue(session.songs, session.index, session.positionMs, playWhenReady = false)
        return true
    }

    /** Media items for MediaSession playback resumption when the player is empty. */
    suspend fun buildSavedSessionItems(): Triple<List<MediaItem>, Int, Long>? {
        val session = savedSession(awaitLibrary(), fallbackToLibrary = true) ?: return null
        return Triple(session.songs.map { it.toMediaItem() }, session.index, session.positionMs)
    }

    // -----------------------------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------------------------

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekToDefaultPosition()
        exoPlayer.play()
    }

    fun pause() = exoPlayer.pause()

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun seekToNext() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
        else if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.seekTo(0, 0)
        }
        exoPlayer.play()
    }

    fun seekToPrevious() {
        if (exoPlayer.currentPosition > 3000 || !exoPlayer.hasPreviousMediaItem()) exoPlayer.seekTo(0)
        else exoPlayer.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _playbackState.value.durationMs
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        _playbackState.update { it.copy(currentPositionMs = target) }
    }

    fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
    }

    fun toggleRepeat() {
        exoPlayer.repeatMode = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped, exoPlayer.playbackParameters.pitch)
        _playbackState.update { it.copy(speed = clamped) }
        scope.launch { preferencesManager.savePlaybackSpeed(clamped) }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        exoPlayer.playbackParameters = PlaybackParameters(exoPlayer.playbackParameters.speed, clamped)
        _playbackState.update { it.copy(pitch = clamped) }
        scope.launch { preferencesManager.savePlaybackPitch(clamped) }
    }

    fun setABLoop(pointA: Long?, pointB: Long?) {
        _playbackState.update { it.copy(loopPointA = pointA, loopPointB = pointB) }
    }

    /** Cycles the A-B repeat: set A → set B (loop starts) → clear. */
    fun cycleABLoop() {
        val s = _playbackState.value
        if (s.currentSong == null) return
        val pos = exoPlayer.currentPosition
        when {
            s.loopPointA == null -> {
                setABLoop(pos, null)
                _messages.tryEmit("Point A set · tap again to set B")
            }
            s.loopPointB == null -> {
                if (pos - s.loopPointA < 1000) {
                    _messages.tryEmit("Point B must be at least 1s after A")
                    return
                }
                setABLoop(s.loopPointA, pos)
                exoPlayer.seekTo(s.loopPointA)
                _messages.tryEmit("Looping A-B")
            }
            else -> {
                setABLoop(null, null)
                _messages.tryEmit("A-B repeat off")
            }
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        exoPlayer.skipSilenceEnabled = enabled
        _playbackState.update { it.copy(isSkipSilenceEnabled = enabled) }
        scope.launch { preferencesManager.saveSkipSilence(enabled) }
    }

    // -----------------------------------------------------------------------------------------
    // Sleep timer
    // -----------------------------------------------------------------------------------------

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        exoPlayer.volume = 1f
        _playbackState.update { it.copy(sleepAtEndOfTrack = false) }
        if (minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }
        val fade = settings.sleepFadeOut
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = ((endAt - SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
                if (fade && remaining <= 30) exoPlayer.volume = (remaining / 30f).coerceIn(0.05f, 1f)
                if (remaining <= 0L) break
                delay(1000)
            }
            exoPlayer.pause()
            exoPlayer.volume = 1f
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
        }
    }

    fun sleepAtEndOfTrack() {
        sleepTimerJob?.cancel()
        exoPlayer.volume = 1f
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = null, sleepAtEndOfTrack = true) }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        exoPlayer.volume = 1f
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = null, sleepAtEndOfTrack = false) }
    }

    // -----------------------------------------------------------------------------------------
    // Headphones & Bluetooth automation
    // -----------------------------------------------------------------------------------------

    private fun onDeviceEvent(event: DeviceEvent) {
        val device = event.device
        if (device.type == AudioDeviceType.SPEAKER) return
        val now = SystemClock.elapsedRealtime()
        when (event) {
            is DeviceEvent.Disconnected -> {
                val wasPlaying = exoPlayer.isPlaying || now - lastPausedAt < 2_000
                if (wasPlaying && settings.pauseOnDisconnect) {
                    exoPlayer.pause()
                    pausedByDisconnectAt = now
                    _messages.tryEmit("${device.name} disconnected · paused")
                }
            }
            is DeviceEvent.Connected -> {
                if (exoPlayer.mediaItemCount == 0 || exoPlayer.isPlaying) return
                if (!PulseMediaSessionService.isRunning) return
                val recentlyInterrupted = pausedByDisconnectAt > 0 && now - pausedByDisconnectAt < 30 * 60_000L
                val shouldResume = (settings.resumeOnReconnect && recentlyInterrupted) ||
                    (settings.bluetoothAutoplay && device.type == AudioDeviceType.BLUETOOTH)
                if (shouldResume) {
                    // Give the Bluetooth route a moment to become active before starting audio.
                    scope.launch {
                        delay(1200)
                        play()
                        _messages.tryEmit("Playing on ${device.name}")
                    }
                } else {
                    _messages.tryEmit("${device.name} connected")
                }
                pausedByDisconnectAt = 0
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Progress & listening history
    // -----------------------------------------------------------------------------------------

    private fun startProgressTracker() {
        scope.launch {
            var ticks = 0
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    _playbackState.update {
                        it.copy(currentPositionMs = pos, durationMs = if (dur != C.TIME_UNSET && dur > 0) dur else it.durationMs)
                    }
                    val state = _playbackState.value
                    if (state.loopPointA != null && state.loopPointB != null && pos >= state.loopPointB) {
                        exoPlayer.seekTo(state.loopPointA)
                    }
                    listenedMs += (250 * exoPlayer.playbackParameters.speed).toLong()
                    maybeStartHistoryEntry()
                    if (++ticks % 20 == 0) persistPosition()
                }
                delay(250)
            }
        }
    }

    private fun maybeStartHistoryEntry() {
        val song = _playbackState.value.currentSong ?: return
        if (historyRowId != null) return
        val threshold = minOf(30_000L, (song.durationMs / 2).coerceAtLeast(5_000L))
        if (listenedMs < threshold) return
        historyRowId = -1L // mark as pending so it is inserted once
        scope.launch(Dispatchers.IO) {
            historyRowId = repository.recordPlayHistory(song, listenedMs, completed = false)
        }
    }

    private fun finishHistoryEntry() {
        val rowId = historyRowId
        val listened = listenedMs
        val duration = _playbackState.value.durationMs
        listenedMs = 0L
        historyRowId = null
        if (rowId != null && rowId > 0) {
            scope.launch(Dispatchers.IO) {
                repository.updatePlayHistory(rowId, listened, completed = duration > 0 && listened >= duration * 0.9)
            }
        }
    }

    private fun persistPosition() {
        val song = _playbackState.value.currentSong ?: return
        val pos = exoPlayer.currentPosition
        scope.launch(Dispatchers.IO) { preferencesManager.savePlaybackState(song.id, pos) }
    }

    fun release() {
        finishHistoryEntry()
        exoPlayer.release()
    }
}
