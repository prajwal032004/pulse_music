package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.AppSettings
import com.example.data.scanner.LibraryData
import com.example.model.*
import com.example.smart.ListeningStats
import com.example.ui.theme.AccentOption
import com.example.ui.theme.ThemeMode
import com.example.util.ArtworkBitmaps
import com.example.ui.components.plural
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class NavigationTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SEARCH("Search"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings")
}

/** What a detail page is showing. */
sealed interface DetailTarget {
    val title: String

    data class AlbumTarget(override val title: String) : DetailTarget
    data class ArtistTarget(override val title: String) : DetailTarget
    data class GenreTarget(override val title: String) : DetailTarget
    data class FolderTarget(val path: String, override val title: String) : DetailTarget
    data class YearTarget(val year: Int) : DetailTarget {
        override val title: String get() = year.toString()
    }
    data class PlaylistTarget(val playlist: CustomPlaylist) : DetailTarget {
        override val title: String get() = playlist.name
    }
    data class SmartTarget(val type: SmartPlaylistType) : DetailTarget {
        override val title: String get() = type.title
    }
}

enum class SmartPlaylistType(val title: String, val subtitle: String) {
    LIKED("Liked Songs", "Everything you've hearted"),
    MOST_PLAYED("Most Played", "Your heavy rotation"),
    RECENTLY_ADDED("Recently Added", "Fresh in your library"),
    RECENTLY_PLAYED("Recently Played", "Pick up where you left off"),
    WORKOUT("Workout", "High energy, high BPM"),
    CHILL("Chill Vibes", "Slow down and unwind"),
    FOCUS("Focus", "Deep work soundtrack"),
    SLEEP("Sleep", "Drift off gently")
}

/** Full-screen destinations layered above the tab scaffold. */
sealed interface Overlay {
    object NowPlaying : Overlay
    object Lyrics : Overlay
    data class AudioLab(val tab: Int = 0) : Overlay
    data class Detail(val target: DetailTarget) : Overlay
    object Stats : Overlay
    object CarMode : Overlay
}

data class PlaylistWithSongs(val playlist: CustomPlaylist, val songs: List<Song>)

data class HomeFeed(
    val recentlyPlayed: List<Song> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val throwback: List<Song> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class PulseMusicViewModel(
    val appContainer: AppContainer
) : ViewModel() {

    private val repository = appContainer.repository
    private val playbackManager = appContainer.playbackManager
    val audioLabManager = appContainer.audioLabManager
    private val prefs = appContainer.preferencesManager
    val smartEngine = appContainer.smartEngine

    val libraryData: StateFlow<LibraryData> = repository.libraryData
    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
    val audioLabState: StateFlow<AudioLabState> = audioLabManager.audioLabState
    val spectrum: StateFlow<List<Float>> = audioLabManager.spectrum

    val settings: StateFlow<AppSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings(onboardingDone = true))
    val recentSearches: StateFlow<List<String>> = prefs.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTab = MutableStateFlow(NavigationTab.HOME)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays: StateFlow<List<Overlay>> = _overlays.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = merge(_toasts, playbackManager.messages, audioLabManager.profileEvents)
        .shareIn(viewModelScope, SharingStarted.Eagerly)

    val userPlaylists: StateFlow<List<PlaylistWithSongs>> = repository.playlists
        .flatMapLatest { entities ->
            if (entities.isEmpty()) flowOf(emptyList())
            else combine(entities.map { e -> repository.getPlaylistSongIds(e.id).map { ids -> e to ids } }) { it.toList() }
        }
        .combine(libraryData) { pairs, lib ->
            val byId = lib.songs.associateBy { it.id }
            pairs.filter { !it.first.isSmart }.map { (e, ids) ->
                PlaylistWithSongs(
                    CustomPlaylist(e.id, e.name, ids.size, e.createdAt, e.isSmart, e.smartType),
                    ids.mapNotNull { byId[it] }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val historyTrigger = combine(libraryData.map { it.songs }.distinctUntilChanged(), repository.recentHistory) { songs, _ -> songs }

    val homeFeed: StateFlow<HomeFeed> = historyTrigger
        .mapLatest {
            HomeFeed(
                recentlyPlayed = smartEngine.getRecentlyPlayed(),
                mostPlayed = smartEngine.getMostPlayed(),
                favorites = smartEngine.getFavorites(),
                recentlyAdded = smartEngine.getRecentlyAdded(20),
                throwback = smartEngine.getThrowbackTracks()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeFeed())

    val stats: StateFlow<ListeningStats> = historyTrigger
        .mapLatest { smartEngine.getListeningStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningStats())

    // -----------------------------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------------------------

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
        _overlays.value = emptyList()
    }

    fun open(overlay: Overlay) {
        val stack = _overlays.value
        if (stack.lastOrNull() == overlay) return
        _overlays.value = stack.filterNot { it == overlay } + overlay
    }

    /** Returns true if something was closed. */
    fun back(): Boolean {
        val stack = _overlays.value
        if (stack.isNotEmpty()) {
            _overlays.value = stack.dropLast(1)
            return true
        }
        if (_currentTab.value != NavigationTab.HOME) {
            _currentTab.value = NavigationTab.HOME
            return true
        }
        return false
    }

    fun close(overlay: Overlay) {
        _overlays.value = _overlays.value.filterNot { it == overlay }
    }

    fun openNowPlaying() {
        if (playbackState.value.currentSong != null) open(Overlay.NowPlaying)
    }

    fun openDetail(target: DetailTarget) = open(Overlay.Detail(target))

    // -----------------------------------------------------------------------------------------
    // Playback
    // -----------------------------------------------------------------------------------------

    fun playSong(song: Song, context: List<Song>? = null) {
        val queue = context ?: libraryData.value.songs
        val index = queue.indexOfFirst { it.id == song.id }
        if (index >= 0) playbackManager.playQueue(queue, index) else playbackManager.playQueue(listOf(song), 0)
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) {
            _toasts.tryEmit("Nothing to play yet")
            return
        }
        if (shuffle) {
            if (playbackState.value.shuffleMode == ShuffleMode.OFF) playbackManager.toggleShuffle()
            playbackManager.playQueue(songs.shuffled(), 0)
        } else {
            playbackManager.playQueue(songs, startIndex)
        }
    }

    fun surpriseMe() = viewModelScope.launch { playSongs(smartEngine.getSurpriseMeMix()) }

    fun playSmart(type: SmartPlaylistType) = viewModelScope.launch { playSongs(smartPlaylistSongs(type)) }

    suspend fun smartPlaylistSongs(type: SmartPlaylistType): List<Song> = when (type) {
        SmartPlaylistType.LIKED -> smartEngine.getFavorites()
        SmartPlaylistType.MOST_PLAYED -> smartEngine.getMostPlayed(50)
        SmartPlaylistType.RECENTLY_ADDED -> smartEngine.getRecentlyAdded()
        SmartPlaylistType.RECENTLY_PLAYED -> smartEngine.getRecentlyPlayed(50)
        SmartPlaylistType.WORKOUT -> smartEngine.getWorkoutMix()
        SmartPlaylistType.CHILL -> smartEngine.getChillEveningMix()
        SmartPlaylistType.FOCUS -> smartEngine.getFocusFlowMix()
        SmartPlaylistType.SLEEP -> smartEngine.getSleepMix()
    }

    fun playNext(song: Song) = playbackManager.playNext(song)
    fun addToQueue(song: Song) = playbackManager.addToQueue(song)
    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun seekToNext() = playbackManager.seekToNext()
    fun seekToPrevious() = playbackManager.seekToPrevious()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun toggleRepeat() = playbackManager.toggleRepeat()
    fun setPlaybackSpeed(speed: Float) = playbackManager.setPlaybackSpeed(speed)
    fun setPitch(pitch: Float) = playbackManager.setPitch(pitch)
    fun cycleABLoop() = playbackManager.cycleABLoop()
    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun sleepAtEndOfTrack() = playbackManager.sleepAtEndOfTrack()
    fun cancelSleepTimer() = playbackManager.cancelSleepTimer()
    fun reorderQueue(from: Int, to: Int) = playbackManager.reorderQueue(from, to)
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)
    fun clearUpNext() = playbackManager.clearUpNext()
    fun skipToQueueItem(index: Int) = playbackManager.skipToQueueItem(index)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val wasFavorite = repository.isFavorite(song.id)
            repository.toggleFavorite(song.id)
            _toasts.tryEmit(if (wasFavorite) "Removed from Liked Songs" else "Added to Liked Songs")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Playlists
    // -----------------------------------------------------------------------------------------

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            initialSongs.forEach { repository.addSongToPlaylist(id, it.id) }
            _toasts.tryEmit("Created \"$name\"")
        }
    }

    fun saveQueueAsPlaylist(name: String) = createPlaylist(name, playbackState.value.queue)

    fun deletePlaylist(playlist: CustomPlaylist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            _overlays.value = _overlays.value.filterNot { it is Overlay.Detail && (it.target as? DetailTarget.PlaylistTarget)?.playlist?.id == playlist.id }
            _toasts.tryEmit("Deleted \"${playlist.name}\"")
        }
    }

    fun addSongToPlaylist(song: Song, playlist: CustomPlaylist) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlist.id, song.id)
            _toasts.tryEmit("Added to ${playlist.name}")
        }
    }

    fun renamePlaylist(playlist: CustomPlaylist, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlist.id, name)
            // The open detail page holds the old name; swap it for the renamed playlist.
            _overlays.value = _overlays.value.map {
                if (it is Overlay.Detail && (it.target as? DetailTarget.PlaylistTarget)?.playlist?.id == playlist.id)
                    Overlay.Detail(DetailTarget.PlaylistTarget(playlist.copy(name = name))) else it
            }
            _toasts.tryEmit("Renamed to \"$name\"")
        }
    }

    suspend fun playCount(song: Song): Int = repository.playCount(song.id)

    fun removeSongFromPlaylist(song: Song, playlistId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, song.id) }
    }

    // -----------------------------------------------------------------------------------------
    // Library, search & settings
    // -----------------------------------------------------------------------------------------

    fun refreshLibrary(announce: Boolean = false) {
        viewModelScope.launch {
            repository.refreshLibrary()
            if (announce) _toasts.tryEmit("Library rescanned · ${(libraryData.value.songs.size).plural("song")}")
        }
    }

    fun addRecentSearch(query: String) = viewModelScope.launch { prefs.addRecentSearch(query) }
    fun clearRecentSearches() = viewModelScope.launch { prefs.clearRecentSearches() }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.saveThemeMode(mode.name) }
    fun setAccent(accent: AccentOption) = viewModelScope.launch { prefs.saveAccent(accent.name) }
    fun setAnimationIntensity(value: Float) = viewModelScope.launch { prefs.saveAnimationIntensity(value) }
    fun setSkipSilence(enabled: Boolean) = playbackManager.setSkipSilence(enabled)
    fun setPauseOnDisconnect(enabled: Boolean) = viewModelScope.launch { prefs.savePauseOnDisconnect(enabled) }
    fun setResumeOnReconnect(enabled: Boolean) = viewModelScope.launch { prefs.saveResumeOnReconnect(enabled) }
    fun setBluetoothAutoplay(enabled: Boolean) = viewModelScope.launch { prefs.saveBluetoothAutoplay(enabled) }
    fun setPerDeviceEq(enabled: Boolean) = viewModelScope.launch { prefs.savePerDeviceEq(enabled) }
    fun setSleepFade(enabled: Boolean) = viewModelScope.launch { prefs.saveSleepFade(enabled) }
    fun setLyricsFontScale(scale: Float) = viewModelScope.launch { prefs.saveLyricsFontScale(scale.coerceIn(0.8f, 1.6f)) }
    fun completeOnboarding() = viewModelScope.launch { prefs.saveOnboardingDone() }
    fun setShakeToSkip(enabled: Boolean) = viewModelScope.launch {
        prefs.saveShakeToSkip(enabled)
        if (enabled) _toasts.tryEmit("Shake your phone twice to skip")
    }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { prefs.saveKeepScreenOn(enabled) }
    fun setMinTrackSeconds(seconds: Int) = viewModelScope.launch { prefs.saveMinTrackSeconds(seconds) }

    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { appContainer.backupManager.export(uri) }
            .onSuccess { _toasts.tryEmit("Backed up ${it.favorites} liked songs · ${it.playlists} playlists") }
            .onFailure { _toasts.tryEmit("Backup failed: ${it.message}") }
    }

    fun importBackup(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { appContainer.backupManager.import(uri) }
            .onSuccess {
                val missing = if (it.missing > 0) " · ${it.missing} not on this device" else ""
                _toasts.tryEmit("Restored ${it.favorites} liked songs · ${it.playlists} playlists$missing")
            }
            .onFailure { _toasts.tryEmit("Restore failed: ${it.message}") }
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    /** Builds the share card image and opens the system share sheet. */
    fun shareSong(context: Context, song: Song, accentArgb: Int, partnerArgb: Int) {
        viewModelScope.launch {
            val uri = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ArtworkBitmaps.renderShareCard(context, song, accentArgb, partnerArgb)
                }
            }.getOrNull()
            val send = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, "Listening to \"${song.title}\" by ${song.artist} on Pulse Music")
                if (uri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }
            context.startActivity(Intent.createChooser(send, "Share now playing").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    class Factory(private val appContainer: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PulseMusicViewModel(appContainer) as T
        }
    }
}
