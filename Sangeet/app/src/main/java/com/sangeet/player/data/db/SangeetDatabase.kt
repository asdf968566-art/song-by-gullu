package com.sangeet.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
        /** v2: gaane ki bhasha (Hindi / Punjabi ...). Purana data safe rehta hai. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN language TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): SangeetDatabase =
            Room.databaseBuilder(context, SangeetDatabase::class.java, "sangeet.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
