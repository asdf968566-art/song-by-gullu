package com.sangeet.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PlaylistRow(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverUrl: String?,
)

@Dao
interface TrackDao {
    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun getBlocking(id: String): TrackEntity?
}

@Dao
abstract class PlaylistDao {
    @Query(
        """
        SELECT p.id AS id, p.name AS name,
            (SELECT COUNT(*) FROM playlist_tracks c WHERE c.playlistId = p.id) AS trackCount,
            (SELECT t.artworkUrl FROM playlist_tracks x JOIN tracks t ON t.id = x.trackId
                WHERE x.playlistId = p.id AND t.artworkUrl IS NOT NULL ORDER BY x.position LIMIT 1) AS coverUrl
        FROM playlists p ORDER BY p.createdAt DESC
        """
    )
    abstract fun observePlaylists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :id ORDER BY pt.position
        """
    )
    abstract fun observeTracks(id: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :id ORDER BY pt.position
        """
    )
    abstract suspend fun getTracks(id: Long): List<TrackEntity>

    @Insert
    abstract suspend fun insert(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylistRow(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    abstract suspend fun clearTracks(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :id")
    abstract suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRefs(refs: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    abstract suspend fun removeTrack(playlistId: Long, trackId: String)

    @Transaction
    open suspend fun delete(id: Long) {
        clearTracks(id)
        deletePlaylistRow(id)
    }

    @Transaction
    open suspend fun addTracks(playlistId: Long, trackIds: List<String>) {
        var pos = maxPosition(playlistId) + 1
        insertRefs(trackIds.distinct().map { PlaylistTrackEntity(playlistId, it, pos++) })
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT t.* FROM tracks t JOIN favorites f ON f.trackId = t.id ORDER BY f.addedAt DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
abstract class HistoryDao {
    @Query(
        """
        SELECT t.* FROM tracks t JOIN history h ON h.trackId = t.id
        ORDER BY h.playedAt DESC LIMIT :limit
        """
    )
    abstract fun observeRecent(limit: Int): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t JOIN history h ON h.trackId = t.id
        ORDER BY h.playCount DESC, h.playedAt DESC LIMIT :limit
        """
    )
    abstract fun observeMostPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT playCount FROM history WHERE trackId = :id")
    abstract suspend fun playCount(id: String): Int?

    @Upsert
    abstract suspend fun upsert(e: HistoryEntity)

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    @Transaction
    open suspend fun recordPlay(id: String) {
        upsert(HistoryEntity(id, System.currentTimeMillis(), (playCount(id) ?: 0) + 1))
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t JOIN downloads d ON d.trackId = t.id
        WHERE d.state = 'DONE' ORDER BY d.createdAt DESC
        """
    )
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun get(trackId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(e: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, progress = :progress WHERE trackId = :trackId")
    suspend fun updateState(trackId: String, state: String, progress: Int)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricsEntity?

    @Upsert
    suspend fun upsert(e: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}
