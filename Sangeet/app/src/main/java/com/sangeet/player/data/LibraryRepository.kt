package com.sangeet.player.data

import com.sangeet.player.data.db.FavoriteEntity
import com.sangeet.player.data.db.PlaylistEntity
import com.sangeet.player.data.db.SangeetDatabase
import com.sangeet.player.data.db.TrackEntity
import com.sangeet.player.data.model.Playlist
import com.sangeet.player.data.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Playlists, liked songs, history aur track cache (Room). */
class LibraryRepository(private val db: SangeetDatabase, scope: CoroutineScope) {

    /** Jo gaane abhi queue mein hain unki memory copy, resolver ke liye. */
    private val memory = ConcurrentHashMap<String, Track>()

    val favoriteIds: StateFlow<Set<String>> = db.favoriteDao().observeIds()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val favorites: Flow<List<Track>> = db.favoriteDao().observeTracks().map { it.map(TrackEntity::toTrack) }
    val recent: Flow<List<Track>> = db.historyDao().observeRecent(30).map { it.map(TrackEntity::toTrack) }
    val mostPlayed: Flow<List<Track>> = db.historyDao().observeMostPlayed(20).map { it.map(TrackEntity::toTrack) }

    val playlists: Flow<List<Playlist>> = db.playlistDao().observePlaylists().map { rows ->
        rows.map { Playlist(it.id, it.name, it.trackCount, it.coverUrl) }
    }

    suspend fun remember(tracks: List<Track>) {
        tracks.forEach { memory[it.id] = it }
        db.trackDao().upsert(tracks.map(TrackEntity::from))
    }

    /** Blocking lookup (player ke loader thread se). */
    fun findBlocking(id: String): Track? = memory[id] ?: db.trackDao().getBlocking(id)?.toTrack()?.also { memory[id] = it }

    suspend fun find(id: String): Track? = memory[id] ?: db.trackDao().get(id)?.toTrack()

    /** Sirf memory se (main thread safe). */
    fun peek(id: String): Track? = memory[id]

    suspend fun toggleFavorite(track: Track) {
        if (track.id in favoriteIds.value) {
            db.favoriteDao().delete(track.id)
        } else {
            remember(listOf(track))
            db.favoriteDao().insert(FavoriteEntity(track.id))
        }
    }

    suspend fun recordPlay(track: Track) {
        remember(listOf(track))
        db.historyDao().recordPlay(track.id)
    }

    suspend fun clearHistory() = db.historyDao().clear()

    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlistDao().observePlaylist(id)
    fun playlistTracks(id: Long): Flow<List<Track>> = db.playlistDao().observeTracks(id).map { it.map(TrackEntity::toTrack) }
    suspend fun playlistTracksOnce(id: Long): List<Track> = db.playlistDao().getTracks(id).map(TrackEntity::toTrack)

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Long {
        val id = db.playlistDao().insert(PlaylistEntity(name = name.ifBlank { "My playlist" }))
        if (tracks.isNotEmpty()) addToPlaylist(id, tracks)
        return id
    }

    suspend fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        remember(tracks)
        db.playlistDao().addTracks(playlistId, tracks.map { it.id })
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) = db.playlistDao().removeTrack(playlistId, trackId)
    suspend fun renamePlaylist(id: Long, name: String) = db.playlistDao().rename(id, name)
    suspend fun deletePlaylist(id: Long) = db.playlistDao().delete(id)
}
