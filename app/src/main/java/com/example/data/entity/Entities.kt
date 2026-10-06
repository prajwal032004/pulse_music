package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val songTitle: String,
    val artistName: String,
    val playedAt: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long = 0,
    val completed: Boolean = false
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isSmart: Boolean = false,
    val smartType: String = "" // "FAVORITES", "RECENTLY_PLAYED", "MOST_PLAYED", "WORKOUT", "CHILL"
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val sortOrder: Int = 0
)

@Entity(tableName = "audio_profiles")
data class AudioProfileEntity(
    @PrimaryKey val deviceType: String, // "SPEAKER", "WIRED_HEADSET", "BLUETOOTH", "USB"
    val profileName: String,
    val eqBandsJson: String,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessGain: Int = 0,
    val spatialEnabled: Boolean = false,
    val balance: Float = 0f
)

@Entity(tableName = "hearing_test_results")
data class HearingTestEntity(
    @PrimaryKey val id: Int = 1,
    val leftEarCurveJson: String, // map of freq -> dB threshold
    val rightEarCurveJson: String,
    val isEnabled: Boolean = false,
    val testDate: Long = System.currentTimeMillis()
)
