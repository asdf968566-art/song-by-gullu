package com.sangeet.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val error: String? = null,
)

/** Seek bar ki position alag flow mein, taaki har 250ms pe poori UI dobara na bane. */
data class PlaybackPosition(val positionMs: Long = 0, val durationMs: Long = 0)

enum class SleepTimerMode { OFF, MINUTES, END_OF_TRACK }

data class SleepTimerState(val mode: SleepTimerMode = SleepTimerMode.OFF, val endsAt: Long = 0)

/** UI aur PlaybackService ke beech ka pul (MediaController). App-wide ek hi instance. */
class PlayerConnection(
    private val context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = _position.asStateFlow()

    private val _sleep = MutableStateFlow(SleepTimerState())
    val sleep: StateFlow<SleepTimerState> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    private var tickJob: Job? = null

    /** Queue khatam hone par milte-julte gaane laane wala (AppContainer set karta hai). */
    var radio: (suspend (seed: Track, exclude: Set<String>) -> List<Track>)? = null
    var autoplayEnabled: () -> Boolean = { false }
    private var radioJob: Job? = null

    /** true = Discover feed chal raha hai; tab feed khud queue bharta hai, radio nahi. */
    var feedActive: Boolean = false
        private set

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending.forEach { it(c) }
            pending.clear()
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && _sleep.value.mode == SleepTimerMode.END_OF_TRACK) {
                player.pause()
                cancelSleepTimer()
            }
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                maybeExtendQueue(player)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val queue = items.mapNotNull { item -> library.peek(item.mediaId) ?: item.toFallbackTrack() }
        val current = c.currentMediaItem?.let { library.peek(it.mediaId) ?: it.toFallbackTrack() }
        _state.value = PlayerState(
            current = current,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            queueIndex = c.currentMediaItemIndex,
            error = c.playerError?.let { it.cause?.message ?: it.message },
        )
        _position.value = PlaybackPosition(_state.value.positionMs, _state.value.durationMs)
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _position.value = PlaybackPosition(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.takeIf { it > 0 } ?: _position.value.durationMs,
                    )
                }
                delay(250)
            }
        }
    }

    private fun MediaItem.toFallbackTrack(): Track? {
        if (mediaId.isBlank()) return null
        val md = mediaMetadata
        return Track(
            id = mediaId,
            source = MediaItems.sourceOf(this) ?: com.sangeet.player.data.model.SourceType.URL,
            sourceId = mediaId.substringAfter(':'),
            title = md.title?.toString() ?: "Unknown",
            artist = md.artist?.toString() ?: "",
            album = md.albumTitle?.toString() ?: "",
            artworkUrl = md.artworkUri?.toString(),
        )
    }

    /** Aakhri gaana baj raha hai -> track record ke hisaab se aur gaane jodo (autoplay). */
    private fun maybeExtendQueue(p: Player) {
        if (feedActive || !autoplayEnabled() || radioJob?.isActive == true) return
        if (p.mediaItemCount == 0 || p.repeatMode != Player.REPEAT_MODE_OFF || p.hasNextMediaItem()) return
        val seed = _state.value.current ?: return
        val source = radio ?: return
        radioJob = scope.launch {
            val exclude = _state.value.queue.mapTo(HashSet()) { it.id }
            val more = runCatching { source(seed, exclude) }.getOrDefault(emptyList())
            if (more.isEmpty()) return@launch
            library.remember(more)
            val c = controller ?: return@launch
            c.addMediaItems(more.map(MediaItems::fromTrack))
            if (c.playbackState == Player.STATE_ENDED) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    // ------------------------------------------------------------ controls

    /** Ek list bajao, [startIndex] wale gaane se. */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, fromFeed: Boolean = false) {
        if (tracks.isEmpty()) return
        feedActive = fromFeed
        radioJob?.cancel()
        scope.launch {
            library.remember(tracks)
            withController { c ->
                c.shuffleModeEnabled = shuffle
                c.setMediaItems(tracks.map(MediaItems::fromTrack), startIndex.coerceIn(tracks.indices), 0L)
                c.prepare()
                c.play()
            }
        }
    }

    fun playNext(track: Track) = scope.launch {
        library.remember(listOf(track))
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(MediaItems.fromTrack(track)); c.prepare(); c.play()
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, MediaItems.fromTrack(track))
            }
        }
    }

    fun addToQueue(tracks: List<Track>) = scope.launch {
        library.remember(tracks)
        withController { c ->
            val wasEmpty = c.mediaItemCount == 0
            c.addMediaItems(tracks.map(MediaItems::fromTrack))
            if (wasEmpty) { c.prepare(); c.play() }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        _position.value = _position.value.copy(positionMs = ms)
    }
    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }
    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ------------------------------------------------------------ sleep timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleep.value = SleepTimerState(SleepTimerMode.MINUTES, endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            withController { it.pause() }
            _sleep.value = SleepTimerState()
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        _sleep.value = SleepTimerState(SleepTimerMode.END_OF_TRACK)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleep.value = SleepTimerState()
    }
}
