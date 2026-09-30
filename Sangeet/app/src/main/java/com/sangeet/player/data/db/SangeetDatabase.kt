package com.sangeet.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        LyricsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SangeetDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        fun create(context: Context): SangeetDatabase =
            Room.databaseBuilder(context, SangeetDatabase::class.java, "sangeet.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
