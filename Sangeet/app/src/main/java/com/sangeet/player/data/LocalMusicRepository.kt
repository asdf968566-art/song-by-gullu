package com.sangeet.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.sangeet.player.data.model.Album
import com.sangeet.player.data.model.Artist
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Phone ke andar ke gaane MediaStore se. */
class LocalMusicRepository(private val context: Context) {

    private val _songs = MutableStateFlow<List<Track>>(emptyList())
    val songs: StateFlow<List<Track>> = _songs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        _scanning.value = true
        try {
            val list = query()
            _songs.value = list
            list
        } catch (e: SecurityException) {
            emptyList()
        } finally {
            _scanning.value = false
        }
    }

    @Suppress("DEPRECATION")
    private fun query(): List<Track> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val out = ArrayList<Track>()
        context.contentResolver.query(
            uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val artist = c.getString(artistCol)
                out += Track(
                    id = Track.makeId(SourceType.LOCAL, id.toString()),
                    source = SourceType.LOCAL,
                    sourceId = id.toString(),
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
                    album = c.getString(albumCol) ?: "",
                    durationMs = c.getLong(durCol),
                    artworkUrl = albumArt(albumId).toString(),
                    streamUrl = ContentUris.withAppendedId(uri, id).toString(),
                    filePath = c.getString(dataCol),
                    albumId = albumId,
                )
            }
        }
        return out
    }

    fun albums(songs: List<Track>): List<Album> = songs.groupBy { it.albumId }.map { (id, tracks) ->
        Album(
            id = id,
            title = tracks.first().album.ifBlank { "Unknown album" },
            artist = tracks.map { it.artist }.distinct().singleOrNull() ?: "Various artists",
            artworkUrl = tracks.first().artworkUrl,
            tracks = tracks,
        )
    }.sortedBy { it.title.lowercase() }

    fun artists(songs: List<Track>): List<Artist> = songs.groupBy { it.artist }.map { (name, tracks) ->
        Artist(name = name, tracks = tracks, artworkUrl = tracks.first().artworkUrl)
    }.sortedBy { it.name.lowercase() }

    companion object {
        private val ALBUM_ART = Uri.parse("content://media/external/audio/albumart")
        fun albumArt(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART, albumId)
    }
}
