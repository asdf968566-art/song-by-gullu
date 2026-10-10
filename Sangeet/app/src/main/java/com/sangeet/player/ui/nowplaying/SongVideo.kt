package com.sangeet.player.ui.nowplaying

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.remote.Http
import com.sangeet.player.data.remote.YouTubeSource
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.components.Artwork
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Now Playing → Video (owner, Oct 10: "jaise YT Music"): the song's YouTube music video in place of the cover,
 * muted and in step with the song, which keeps playing from the app's player (notification, lock screen and
 * background stay as they are). [onNone]: no video for this song (or YouTube said no); Now Playing goes back to Song.
 */
@Composable
fun SongVideo(track: Track, modifier: Modifier = Modifier, onNone: () -> Unit) {
    val c = LocalAppContainer.current
    val none by rememberUpdatedState(onNone)
    var video by remember(track.id) { mutableStateOf<YouTubeSource.Video?>(null) }
    var aspect by remember(track.id) { mutableFloatStateOf(16f / 9f) }
    LaunchedEffect(track.id) {
        video = runCatching { c.online.youtube.videoFor(track) }
            .onFailure { android.util.Log.w("Sangeet", "video for ${track.title}: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()
        if (video == null) none()
    }
    val v = video
    Box(
        modifier.aspectRatio(if (v == null) 1f else aspect).clip(RoundedCornerShape(10.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (v == null) {
            Artwork(track.artworkUrl, Modifier.fillMaxSize(), shape = RoundedCornerShape(10.dp), seed = track.title)
            CircularProgressIndicator(Modifier.size(36.dp), color = Color.White)
        } else {
            VideoPicture(v, onAspect = { aspect = it }, onError = { none() })
        }
    }
}

/** The video's picture, muted, kept with the app player's position and play/pause. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPicture(v: YouTubeSource.Video, onAspect: (Float) -> Unit, onError: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val aspectChanged by rememberUpdatedState(onAspect)
    val failed by rememberUpdatedState(onError)
    val player = remember(v.url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client)))
            .build()
            .apply {
                volume = 0f
                setMediaItem(MediaItem.fromUri(v.url))
                prepare()
                seekTo(c.player.position.value.positionMs)
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectChanged(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("Sangeet", "video ${v.id}: ${error.errorCodeName}")
                failed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // Keep the picture with the song: the same play/pause, and a jump back when it drifts.
    LaunchedEffect(player) {
        var lastSeek = 0L
        while (true) {
            val playing = c.player.state.value.isPlaying
            player.playWhenReady = playing
            val audio = c.player.position.value.positionMs
            val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val now = System.currentTimeMillis()
            if (player.playbackState == Player.STATE_READY && abs(player.currentPosition - audio) > 800 &&
                audio < end && now - lastSeek > 3_000
            ) {
                lastSeek = now
                player.seekTo(audio + if (playing) 300 else 0)
            }
            delay(500)
        }
    }
    key(player) {
        AndroidView(factory = { ctx -> TextureView(ctx).also(player::setVideoTextureView) }, modifier = Modifier.fillMaxSize())
    }
}

/** "Song | Video" above the cover, like YouTube Music. */
@Composable
fun SongVideoSwitch(video: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(3.dp)) {
            listOf(false to "Song", true to "Video").forEach { (isVideo, label) ->
                val on = isVideo == video
                Text(
                    label,
                    color = if (on) Color.Black else color.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                        .clickable { onChange(isVideo) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }
    }
}
