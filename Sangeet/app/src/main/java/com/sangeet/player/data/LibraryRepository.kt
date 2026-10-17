package com.sangeet.player.data

import com.sangeet.player.data.db.FavoriteEntity
import com.sangeet.player.data.db.PlaylistEntity
import com.sangeet.player.data.db.SangeetDatabase
import com.sangeet.player.data.db.TrackEntity
import com.sangeet.player.data.model.Playlist
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.recommend.PlayedTrack
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
        // Live radio isn't a song you played: it stays out of history, stats and suggestions.
        if (track.source == com.sangeet.player.data.model.SourceType.URL && track.sourceId.startsWith("radio-")) return
        remember(listOf(track))
        db.historyDao().recordPlay(track.id)
    }

    suspend fun clearHistory() = db.historyDao().clear()

    /** Suni hui history, sabse naye pehle (recommendations ke liye). */
    suspend fun playedHistory(limit: Int = 500): List<PlayedTrack> =
        db.historyDao().played(limit).map { PlayedTrack(it.track.toTrack(), it.playCount, it.playedAt) }

    suspend fun favoritesOnce(): List<Track> = db.favoriteDao().tracksOnce().map(TrackEntity::toTrack)

    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlistDao().observePlaylist(id)
    fun playlistTracks(id: Long): Flow<List<Track>> = db.playlistDao().observeTracks(id).map { it.map(TrackEntity::toTrack) }
    suspend fun playlistTracksOnce(id: Long): List<Track> = db.playlistDao().getTracks(id).map(TrackEntity::toTrack)

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Long {
        val id = db.playlistDao().insert(PlaylistEntity(name = name.ifBlank { "My playlist" }))
        if (tracks.isNotEmpty()) addToPlaylist(id, tracks)
        return id
    }

    /**
     * Adds the songs the playlist doesn't have yet (report #55): the same song from another source or album is
     * already there; another version or singer isn't ([SameSong]). Returns how many were added.
     */
    suspend fun addToPlaylist(playlistId: Long, tracks: List<Track>): Int {
        val have = HashMap<String, MutableList<SameSong.Key>>()
        val ids = HashSet<String>()
        fun keep(t: Track) {
            ids += t.id
            val k = SameSong.key(t)
            have.getOrPut(k.title) { ArrayList() } += k
        }
        playlistTracksOnce(playlistId).forEach(::keep)
        val add = tracks.filter { t ->
            val k = SameSong.key(t)
            val dup = t.id in ids || have[k.title].orEmpty().any { it.sameAs(k) }
            if (!dup) keep(t)
            !dup
        }
        android.util.Log.i("Sangeet", "playlist add: added ${add.size} of ${tracks.size} to $playlistId")
        if (add.isEmpty()) return 0
        remember(add)
        db.playlistDao().addTracks(playlistId, add.map { it.id })
        return add.size
    }

    suspend fun replacePlaylistTracks(playlistId: Long, tracks: List<Track>) {
        remember(tracks)
        db.playlistDao().replaceTracks(playlistId, tracks.map { it.id })
    }

    suspend fun playlistExists(id: Long): Boolean = db.playlistDao().count(id) > 0

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) = db.playlistDao().removeTrack(playlistId, trackId)
    suspend fun renamePlaylist(id: Long, name: String) = db.playlistDao().rename(id, name)
    suspend fun deletePlaylist(id: Long) = db.playlistDao().delete(id)
}
