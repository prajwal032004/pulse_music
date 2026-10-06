package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // Favorites
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    // History
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<PlayHistoryEntity>>

    @Insert
    suspend fun insertPlayHistory(history: PlayHistoryEntity): Long

    @Query("UPDATE play_history SET durationPlayedMs = :durationMs, completed = :completed WHERE id = :id")
    suspend fun updatePlayHistory(id: Long, durationMs: Long, completed: Boolean)

    @Query("SELECT * FROM play_history WHERE playedAt >= :since ORDER BY playedAt DESC")
    suspend fun getHistorySince(since: Long): List<PlayHistoryEntity>

    @Query("SELECT songId, COUNT(*) as count FROM play_history GROUP BY songId ORDER BY count DESC LIMIT :limit")
    suspend fun getMostPlayedSongIds(limit: Int = 50): List<SongPlayCount>

    @Query("SELECT COUNT(*) FROM play_history")
    fun getTotalPlaysCount(): Flow<Int>

    @Query("SELECT SUM(durationPlayedMs) FROM play_history")
    fun getTotalMinutesPlayed(): Flow<Long?>

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM play_history WHERE songId = :songId")
    suspend fun getPlayCount(songId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: Long): Int

    @Query("SELECT * FROM playlists")
    suspend fun getPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getPlaylistSongIdsOnce(playlistId: Long): List<Long>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getPlaylistSongIds(playlistId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    // Audio Profiles
    @Query("SELECT * FROM audio_profiles WHERE deviceType = :deviceType LIMIT 1")
    suspend fun getAudioProfile(deviceType: String): AudioProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAudioProfile(profile: AudioProfileEntity)

    // Hearing Test
    @Query("SELECT * FROM hearing_test_results WHERE id = 1")
    fun getHearingTest(): Flow<HearingTestEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHearingTest(test: HearingTestEntity)
}

data class SongPlayCount(
    val songId: Long,
    val count: Int
)
