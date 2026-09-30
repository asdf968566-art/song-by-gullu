package com.sangeet.player.playback

import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import com.google.common.collect.ImmutableList
import com.sangeet.player.data.model.Track
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.flow.first
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sangeet.player.MainActivity
import com.sangeet.player.SangeetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background mein gaana bajane wali service (notification + lock screen + Bluetooth + Android Auto).
 * MediaLibraryService hai taaki car ki screen pe Liked / Recent / Daily Mix / Charts browse ho sakein.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var pausedByNoisyAt = 0L
    private var deviceCallback: AudioDeviceCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var errorStreak = 0

    override fun onCreate() {
        super.onCreate()
        val container = (application as SangeetApplication).container

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.dataSourceFactory))
            // 1 second buffer hote hi bajna shuru (default 2.5s) -> gaana jaldi chalta hai.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 60_000, 1_000, 2_000)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        container.equalizer.attach(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                container.equalizer.attach(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                scope.launch { container.library.find(id)?.let { container.library.recordPlay(it) } }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) errorStreak = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                pausedByNoisyAt = when {
                    playWhenReady -> 0L
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> System.currentTimeMillis()
                    else -> 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Ek gaana na chale to agla try karo (max 3 baar lagataar).
                errorStreak++
                if (errorStreak <= 3 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        scope.launch {
            container.settings.settings.collect { s ->
                player.skipSilenceEnabled = s.skipSilence
                if (player.playbackParameters.speed != s.playbackSpeed) player.setPlaybackSpeed(s.playbackSpeed)
            }
        }

        // Headphone / Bluetooth nikalne se ruka gaana, wapas lagate hi (30 min ke andar) chalu.
        val audio = getSystemService(AudioManager::class.java)
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (pausedByNoisyAt == 0L || !container.settings.current.headphoneResume) return
                if (System.currentTimeMillis() - pausedByNoisyAt > 30 * 60_000L) return
                if (addedDevices.any { it.isSink && it.type in HEADSET_TYPES }) {
                    pausedByNoisyAt = 0L
                    player.play()
                }
            }
        }.also { audio.registerAudioDeviceCallback(it, Handler(Looper.getMainLooper())) }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Sangeet"), params))

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
                    val items = runCatching { children(parentId) }.getOrDefault(emptyList())
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val t = container.library.peek(mediaId)
                    return Futures.immediateFuture(
                        if (t != null) LibraryResult.ofItem(MediaItems.fromTrack(t), null)
                        else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    )
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val playable = mediaItems.map { item ->
                        val hls = container.library.peek(item.mediaId)?.streamUrl?.takeIf { it.contains(".m3u8") }
                        if (hls != null) item.buildUpon().setUri(Uri.parse(hls)).build()
                        else MediaItems.withUri(item)
                    }
                    return Futures.immediateFuture(playable.toMutableList())
                }
            })
            .setSessionActivity(openApp)
            .build()
    }

    // ------------------------------------------------------------ Android Auto browse tree

    private fun folder(id: String, title: String, subtitle: String? = null, art: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private suspend fun children(parentId: String): List<MediaItem> {
        val c = (application as SangeetApplication).container
        suspend fun tracks(list: List<Track>): List<MediaItem> {
            val some = list.take(100)
            c.library.remember(some)
            return some.map(MediaItems::fromTrack)
        }
        return when {
            parentId == ROOT -> listOf(
                folder(LIKED, "Liked Songs"),
                folder(DAILY, "Daily Mix"),
                folder(RECENT, "Recently played"),
                folder(DOWNLOADS, "Downloads"),
                folder(CHARTS, "Top Charts"),
            )
            parentId == LIKED -> tracks(c.library.favoritesOnce())
            parentId == RECENT -> tracks(c.library.playedHistory(100).map { it.track })
            parentId == DOWNLOADS -> tracks(c.downloads.downloadedTracks.first())
            parentId == DAILY -> tracks(c.recommendations.refreshMixes().firstOrNull()?.tracks.orEmpty())
            parentId == CHARTS -> c.online.saavn.charts(c.settings.current).map { folder("$CHART_PREFIX${it.id}", it.title, it.subtitle, it.artworkUrl) }
            parentId.startsWith(CHART_PREFIX) -> tracks(c.online.saavn.playlistTracks(parentId.removePrefix(CHART_PREFIX)))
            else -> emptyList()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        deviceCallback?.let { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(it) }
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val RECENT = "recent"
        const val DAILY = "daily"
        const val DOWNLOADS = "downloads"
        const val CHARTS = "charts"
        const val CHART_PREFIX = "chart:"

        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
