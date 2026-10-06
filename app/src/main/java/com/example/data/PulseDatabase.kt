package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.MusicDao
import com.example.data.entity.*

@Database(
    entities = [
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        AudioProfileEntity::class,
        HearingTestEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        fun getInstance(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "pulse_music.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
