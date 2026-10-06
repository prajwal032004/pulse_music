package com.example.data

import android.content.Context
import com.example.data.dao.MusicDao
import com.example.data.entity.FavoriteEntity
import com.example.data.entity.HearingTestEntity
import com.example.data.entity.PlayHistoryEntity
import com.example.data.entity.PlaylistEntity
import com.example.data.entity.PlaylistSongCrossRef
import com.example.data.scanner.LibraryData
import com.example.data.scanner.MediaStoreScanner
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class MusicRepository(
    private val context: Context,
    private val dao: MusicDao,
    private val preferencesManager: PreferencesManager
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanner = MediaStoreScanner(context)

    private val _libraryData = MutableStateFlow(LibraryData())
    val libraryData: StateFlow<LibraryData> = _libraryData.asStateFlow()

    val favoriteIds: Flow<List<Long>> = dao.getAllFavoriteIds()
    val playlists: Flow<List<PlaylistEntity>> = dao.getAllPlaylists()
    val recentHistory: Flow<List<PlayHistoryEntity>> = dao.getRecentHistory(100)
    val totalPlays: Flow<Int> = dao.getTotalPlaysCount()
    val hearingTestEntity: Flow<HearingTestEntity?> = dao.getHearingTest()

    private var pendingAutoScan: kotlinx.coroutines.Job? = null
    private val scanMutex = kotlinx.coroutines.sync.Mutex()

    init {
        // Observe favorites and refresh library items' isFavorite status
        repositoryScope.launch {
            favoriteIds.collect { favs ->
                val favSet = favs.toSet()
                _libraryData.update { current ->
                    current.copy(
                        songs = current.songs.map { it.copy(isFavorite = favSet.contains(it.id)) }
                    )
                }
            }
        }

        // Initial scan
        repositoryScope.launch {
            refreshLibrary()
        }

        // Rescan automatically when songs are downloaded, added or deleted on the device.
        runCatching {
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        pendingAutoScan?.cancel()
                        pendingAutoScan = repositoryScope.launch {
                            kotlinx.coroutines.delay(2500) // let a download / batch copy settle
                            refreshLibrary()
                        }
                    }
                }
            )
        }
    }

    suspend fun refreshLibrary() = scanMutex.withLock {
        val favs = dao.getAllFavoriteIds().firstOrNull()?.toSet() ?: emptySet()
        val data = scanner.scanAudioLibrary(favs, preferencesManager.minTrackSeconds.first())
        _libraryData.value = data
    }

    suspend fun toggleFavorite(songId: Long) {
        val currentFavs = dao.getAllFavoriteIds().firstOrNull() ?: emptyList()
        if (currentFavs.contains(songId)) {
            dao.removeFavorite(songId)
        } else {
            dao.addFavorite(FavoriteEntity(songId = songId))
        }
    }

    suspend fun recordPlayHistory(song: Song, durationPlayedMs: Long, completed: Boolean): Long {
        return dao.insertPlayHistory(
            PlayHistoryEntity(
                songId = song.id,
                songTitle = song.title,
                artistName = song.artist,
                durationPlayedMs = durationPlayedMs,
                completed = completed
            )
        )
    }

    suspend fun updatePlayHistory(id: Long, durationPlayedMs: Long, completed: Boolean) {
        dao.updatePlayHistory(id, durationPlayedMs, completed)
    }

    suspend fun getHistorySince(since: Long): List<PlayHistoryEntity> = dao.getHistorySince(since)

    suspend fun getMostPlayedSongIds(limit: Int = 50): List<Long> = dao.getMostPlayedSongIds(limit).map { it.songId }

    suspend fun isFavorite(songId: Long): Boolean = dao.getAllFavoriteIds().firstOrNull()?.contains(songId) == true

    suspend fun createPlaylist(name: String, isSmart: Boolean = false, smartType: String = ""): Long {
        return dao.insertPlaylist(
            PlaylistEntity(
                name = name,
                isSmart = isSmart,
                smartType = smartType
            )
        )
    }

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name)

    suspend fun playCount(songId: Long): Int = dao.getPlayCount(songId)

    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        // Append to the end so playlists keep the order songs were added in.
        val next = dao.getMaxSortOrder(playlistId) + 1
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId, sortOrder = next))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getPlaylistSongIds(playlistId: Long): Flow<List<Long>> {
        return dao.getPlaylistSongIds(playlistId)
    }

    suspend fun saveHearingTestResults(
        leftCurveJson: String,
        rightCurveJson: String,
        enabled: Boolean
    ) {
        dao.saveHearingTest(
            HearingTestEntity(
                id = 1,
                leftEarCurveJson = leftCurveJson,
                rightEarCurveJson = rightCurveJson,
                isEnabled = enabled,
                testDate = System.currentTimeMillis()
            )
        )
    }

    fun getPreferences(): PreferencesManager = preferencesManager
}
