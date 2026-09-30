package com.sangeet.player.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String?,
    val filePath: String?,
    val albumId: Long,
    @ColumnInfo(defaultValue = "") val language: String = "",
) {
    fun toTrack() = Track(
        id = id,
        source = runCatching { SourceType.valueOf(source) }.getOrDefault(SourceType.URL),
        sourceId = sourceId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        filePath = filePath,
        albumId = albumId,
        language = language,
    )

    companion object {
        fun from(t: Track) = TrackEntity(
            id = t.id, source = t.source.name, sourceId = t.sourceId, title = t.title,
            artist = t.artist, album = t.album, durationMs = t.durationMs,
            artworkUrl = t.artworkUrl, streamUrl = t.streamUrl, filePath = t.filePath, albumId = t.albumId, language = t.language,
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val trackId: String,
    val playedAt: Long,
    val playCount: Int,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val state: String,
    val progress: Int,
    val filePath: String?,
    val quality: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val synced: String?,
    val plain: String?,
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** Kaunsa gaana kab aur kitni der suna — Stats (Wrapped jaisa) ke liye. */
@Entity(tableName = "listen_log")
data class ListenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val startedAt: Long,
    val playedMs: Long,
)
