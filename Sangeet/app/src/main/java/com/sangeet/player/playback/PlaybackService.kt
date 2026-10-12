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
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sangeet.player.MainActivity
import com.sangeet.player.SangeetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    /** When the internet went away during the current song (0: it didn't), and the job waiting for it. */
    private var netLostAt = 0L
    private var netJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as SangeetApplication).container

        val player = ExoPlayer.Builder(this)
            // A short internet drop is tried again for a while before it counts as an error (report #41).
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(container.dataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(8))
            )
            // 1 second buffer hote hi bajna shuru (default 2.5s) -> gaana jaldi chalta hai. It keeps up to 3 minutes
            // ahead (by time, not size), so a slow or broken connection doesn't stop the song (report #41).
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(50_000, 180_000, 1_000, 2_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
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
                netLostAt = 0L
                val id = mediaItem?.mediaId ?: return
                container.recommendations.markSeen(listOf(id))
                scope.launch { container.library.find(id)?.let { container.library.recordPlay(it) } }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    errorStreak = 0
                    netLostAt = 0L
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                pausedByNoisyAt = when {
                    playWhenReady -> 0L
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> System.currentTimeMillis()
                    else -> 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // The internet dropped or got too slow: wait for it on this song, don't jump to the next one.
                if (isNetworkProblem(error)) {
                    waitForInternet(player)
                    return
                }
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

        // The session (app, notification, lock screen, headset buttons) gets the player that fades pause and play.
        val fading = FadingPlayer(player)

        // Headphone / Bluetooth nikalne se ruka gaana, wapas lagate hi (30 min ke andar) chalu.
        val audio = getSystemService(AudioManager::class.java)
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (pausedByNoisyAt == 0L || !container.settings.current.headphoneResume) return
                if (System.currentTimeMillis() - pausedByNoisyAt > 30 * 60_000L) return
                if (addedDevices.any { it.isSink && it.type in HEADSET_TYPES }) {
                    pausedByNoisyAt = 0L
                    fading.play()
                }
            }
        }.also { audio.registerAudioDeviceCallback(it, Handler(Looper.getMainLooper())) }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, fading, object : MediaLibrarySession.Callback {
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

    // ------------------------------------------------------------ the internet goes away during a song

    /**
     * A load failed because the phone has no internet, or the connection broke or timed out. Not for offline mode
     * (then only phone songs play) or a link that doesn't work (a bad link skips to the next song as before).
     */
    private fun isNetworkProblem(e: PlaybackException): Boolean {
        val c = (application as SangeetApplication).container
        if (c.settings.current.offlineMode) return false
        if (!c.network.status.value.online) return true
        if (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) return true
        var cause: Throwable? = e.cause
        while (cause != null) {
            if (cause is java.net.SocketTimeoutException || cause is java.net.UnknownHostException ||
                cause is java.net.ConnectException || cause is java.net.SocketException || cause is javax.net.ssl.SSLException
            ) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Report #41 (Oct 12): "network slow ho jaaye ya down ho jaye to instant next song pe switch na ho, 30 sec network
     * ka wait karna hai". The song stays where it stopped and is tried again whenever the internet is there, for 30
     * seconds. After that, a downloaded song or phone file further on plays; with none, the song waits here and goes
     * on by itself when the internet comes back (within 10 minutes).
     */
    private fun waitForInternet(player: ExoPlayer) {
        val c = (application as SangeetApplication).container
        if (netLostAt == 0L) netLostAt = System.currentTimeMillis()
        netJob?.cancel()
        netJob = scope.launch {
            android.util.Log.i("Sangeet", "internet problem during a song: waiting for it (${(System.currentTimeMillis() - netLostAt) / 1000}s so far)")
            val since = netLostAt
            while (System.currentTimeMillis() - since < NET_WAIT_MS) {
                delay(2_000)
                if (player.playerError == null) return@launch // something else is playing now
                if (c.network.status.value.online) {
                    player.prepare() // the same song from the same place; if it fails again, this goes on waiting
                    return@launch
                }
            }
            if (player.playerError == null) return@launch
            netLostAt = 0L
            val start = player.currentMediaItemIndex + 1
            val onPhone = (start until player.mediaItemCount).firstOrNull { i ->
                val id = player.getMediaItemAt(i).mediaId
                c.downloads.localPath(id) != null || c.library.peek(id)?.source == com.sangeet.player.data.model.SourceType.LOCAL
            }
            if (onPhone != null) {
                android.util.Log.i("Sangeet", "no internet for 30 s: playing a song that's on the phone")
                player.seekToDefaultPosition(onPhone)
                player.prepare()
                player.play()
                return@launch
            }
            android.util.Log.i("Sangeet", "no internet for 30 s: the song waits for it")
            val back = withTimeoutOrNull(10 * 60_000L) { c.network.status.first { it.online } }
            if (back != null && player.playerError != null) player.prepare()
        }
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
        const val NET_WAIT_MS = 30_000L

        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
