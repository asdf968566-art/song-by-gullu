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

    /** Gaano ke beech fade kitna (ms). AppContainer settings se set karta hai. */
    var crossfadeMs: () -> Long = { 0L }
    /** Suna hua time record karne wala (Stats). */
    var onListened: (suspend (track: Track, startedAt: Long, playedMs: Long) -> Unit)? = null
    private var listenTrack: Track? = null
    private var listenStart = 0L
    private var listenMs = 0L
    private var lastVolume = 1f

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
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) flushListen()
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
                    accumulateListen(250)
                    applyVolume(c)
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

    // ------------------------------------------------------------ listening record (Stats)

    private fun accumulateListen(ms: Long) {
        val cur = _state.value.current ?: return
        if (listenTrack?.id != cur.id) {
            flushListen()
            listenTrack = cur
            listenStart = System.currentTimeMillis()
            listenMs = 0
        }
        listenMs += ms
    }

    /** 10 second se kam suna to gina nahi jaata. */
    private fun flushListen() {
        val t = listenTrack ?: return
        val ms = listenMs
        val start = listenStart
        listenTrack = null
        listenMs = 0
        if (ms < 10_000) return
        val sink = onListened ?: return
        scope.launch { runCatching { sink(t, start, ms) } }
    }

    // ------------------------------------------------------------ fades (crossfade + sleep timer)

    /** Gaane ke shuru/aakhir mein awaaz dheere badhe/ghate, aur sleep timer ke aakhri 30s mein dheere band. */
    private fun applyVolume(c: MediaController) {
        val pos = c.currentPosition
        val dur = c.duration
        val fade = crossfadeMs()
        var v = 1f
        if (fade > 0 && dur > fade * 3) {
            if (pos < fade) v = minOf(v, (pos.toFloat() / fade).coerceIn(0.05f, 1f))
            val left = dur - pos
            if (left < fade && c.hasNextMediaItem()) v = minOf(v, (left.toFloat() / fade).coerceIn(0f, 1f))
        }
        val sleep = _sleep.value
        if (sleep.mode == SleepTimerMode.MINUTES) {
            val left = sleep.endsAt - System.currentTimeMillis()
            if (left < SLEEP_FADE_MS) v = minOf(v, (left.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f))
        } else if (sleep.mode == SleepTimerMode.END_OF_TRACK && dur > 0) {
            val left = dur - pos
            if (left < 10_000) v = minOf(v, (left / 10_000f).coerceIn(0f, 1f))
        }
        if (kotlin.math.abs(v - lastVolume) > 0.01f) {
            lastVolume = v
            c.volume = v
        }
    }

    private fun resetVolume() {
        lastVolume = 1f
        controller?.volume = 1f
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
    fun play(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        fromFeed: Boolean = false,
        startPositionMs: Long = 0L,
    ) {
        if (tracks.isEmpty()) return
        feedActive = fromFeed
        radioJob?.cancel()
        scope.launch {
            library.remember(tracks)
            withController { c ->
                c.shuffleModeEnabled = shuffle
                // Naya gaana/list = "ek hi gaana repeat" band (user khud dobara chala sakta hai)
                if (c.repeatMode == Player.REPEAT_MODE_ONE) c.repeatMode = Player.REPEAT_MODE_OFF
                c.setMediaItems(tracks.map(MediaItems::fromTrack), startIndex.coerceIn(tracks.indices), startPositionMs)
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

    /** Puts [tracks] into the songs still to come, taking turns with them: the AI DJ's late picks. */
    fun mixIntoQueue(tracks: List<Track>) = scope.launch {
        if (tracks.isEmpty()) return@launch
        library.remember(tracks)
        withController { c ->
            var at = c.currentMediaItemIndex + 1
            for (t in tracks) {
                val pos = at.coerceIn(0, c.mediaItemCount)
                c.addMediaItem(pos, MediaItems.fromTrack(t))
                at = pos + 2
            }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun setPlaying(on: Boolean) = withController { if (on) it.play() else it.pause() }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        _position.value = _position.value.copy(positionMs = ms)
    }
    fun skipTo(index: Int, positionMs: Long = 0L) = withController {
        if (positionMs > 0) it.seekTo(index, positionMs) else it.seekToDefaultPosition(index)
        it.play()
    }

    /** Radio: ye gaana + iske jaise gaane, lagatar (autoplay aage bhi bharta rahega). */
    fun startRadio(seed: Track) {
        play(listOf(seed))
        val source = radio ?: return
        radioJob = scope.launch {
            val more = runCatching { source(seed, setOf(seed.id)) }.getOrDefault(emptyList())
            if (more.isNotEmpty()) addToQueue(more)
        }
    }
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
        resetVolume()
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleep.value = SleepTimerState(SleepTimerMode.MINUTES, endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            withController { it.pause() }
            _sleep.value = SleepTimerState()
            delay(500)
            resetVolume()
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        _sleep.value = SleepTimerState(SleepTimerMode.END_OF_TRACK)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleep.value = SleepTimerState()
        resetVolume()
    }

    private companion object {
        const val SLEEP_FADE_MS = 30_000L
    }
}
