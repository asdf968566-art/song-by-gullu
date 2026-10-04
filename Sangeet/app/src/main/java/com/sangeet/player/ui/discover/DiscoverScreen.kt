package com.sangeet.player.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.lyrics.Lyrics
import com.sangeet.player.data.Mood
import com.sangeet.player.data.Moods
import com.sangeet.player.data.model.inLanguages
import com.sangeet.player.ui.Routes
import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf as floatState
import com.sangeet.player.ui.components.formatDuration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.nowplaying.LyricsView
import com.sangeet.player.data.recommend.Suggestion
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.Artwork
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoverViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val items: List<Suggestion> = emptyList(),
        val loading: Boolean = true,
        val exhausted: Boolean = false,
        /** null = "For You" (track record se); warna chuna hua mood. */
        val mood: Mood? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var loadingMore = false
    private var moodPage = 0

    init { loadMore() }

    fun loadMore() {
        if (loadingMore || _ui.value.exhausted) return
        loadingMore = true
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val have = _ui.value.items.mapTo(HashSet()) { it.track.id }
            val mood = _ui.value.mood
            val more = runCatching {
                if (mood == null) c.recommendations.suggestions(limit = 25, exclude = have)
                else moodBatch(mood, have)
            }.getOrDefault(emptyList())
            if (mood != _ui.value.mood) { loadingMore = false; return@launch }
            _ui.value = _ui.value.copy(items = _ui.value.items + more, loading = false, exhausted = more.isEmpty())
            // Feed chal raha hai to naye gaane queue ke aakhir mein bhi jodo.
            if (more.isNotEmpty() && c.player.feedActive) c.player.addToQueue(more.map { it.track })
            loadingMore = false
        }
    }

    fun refresh() {
        _ui.value = Ui(mood = _ui.value.mood)
        moodPage = 0
        loadingMore = false
        loadMore()
    }

    fun setMood(m: Mood?) {
        if (m == _ui.value.mood) return
        _ui.value = Ui(mood = m)
        moodPage = 0
        loadingMore = false
        loadMore()
    }

    /** Left swipe: ye gaana / artist kam dikhao. */
    fun dislike(t: Track) = c.recommendations.dislike(t)

    /** Mood ke gaane: pasandida bhasha + mood, har page pe thodi alag query (endless feed). */
    private suspend fun moodBatch(mood: Mood, have: Set<String>): List<Suggestion> = kotlinx.coroutines.coroutineScope {
        val variants = listOf("", "new", "best", "hits", "2024", "90s", "latest", "top")
        val v = variants[moodPage % variants.size]
        moodPage++
        Moods.queries(mood, c.settings.current.languages)
            .map { q -> async { runCatching { c.online.searchAll("$q $v".trim()) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
            .filter { it.id !in have && it.inLanguages(c.settings.current.languages) }
            .shuffled()
            .take(25)
            .map { Suggestion(it, "${mood.emoji} ${mood.name} mood") }
    }
}

/** Reels jaisa feed: upar scroll karo, agla gaana apne aap bajega. Aapke track record se. */
@Composable
fun DiscoverScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val vm = appViewModel { DiscoverViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state by c.player.state.collectAsStateWithLifecycle()
    val favorites by c.library.favoriteIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val pos = c.player.position.collectAsStateWithLifecycle()
    val items by rememberUpdatedState(ui.items)
    // Chal rahe gaane ke lyrics (Resso jaisa feed pe hi dikhte hain)
    var lyrics by remember { mutableStateOf<Pair<String, Lyrics?>?>(null) }
    LaunchedEffect(state.current?.id) {
        val t = state.current ?: return@LaunchedEffect
        lyrics = t.id to runCatching { c.lyrics.get(t, allowOnline = settings.autoLyrics) }.getOrNull()
    }
    val pager = rememberPagerState { items.size }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    fun playPage(page: Int) {
        val list = vm.ui.value.items
        val t = list.getOrNull(page)?.track ?: return
        val ps = c.player.state.value
        if (ps.current?.id == t.id) return
        val inQueue = if (c.player.feedActive) ps.queue.indexOfFirst { it.id == t.id } else -1
        // Hook preview: lambe gaane chorus ke paas (~30%) se shuru, reels jaisa
        val hook = if (c.settings.current.hookPreview && t.durationMs > 90_000) (t.durationMs * 0.3).toLong().coerceAtMost(75_000) else 0L
        if (inQueue >= 0) c.player.skipTo(inQueue, hook)
        else c.player.play(list.map { it.track }, page, fromFeed = true, startPositionMs = hook)
    }

    // Kuch bhi apne aap shuru nahi hota: pehla gaana user khud Play dabakar chalata hai.
    // Uske baad scroll karte hi agla gaana bajta hai.
    var armed by remember { mutableStateOf(c.player.feedActive) }
    LaunchedEffect(pager, ui.items.isNotEmpty()) {
        if (vm.ui.value.items.isEmpty()) return@LaunchedEffect
        snapshotFlow { pager.settledPage }.collect { page ->
            if (armed) playPage(page)
            if (page >= vm.ui.value.items.size - 5) vm.loadMore()
        }
    }

    // Gaana khatam hua aur agla shuru hua -> feed bhi agle page par.
    LaunchedEffect(state.current?.id) {
        val id = state.current?.id ?: return@LaunchedEffect
        if (!c.player.feedActive) return@LaunchedEffect
        val idx = items.indexOfFirst { it.track.id == id }
        if (idx >= 0 && idx != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(idx)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ui.items.isEmpty() && ui.loading -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Sangeet.spec.accent)
                Spacer(Modifier.height(12.dp))
                Text("Finding songs for you…", color = Color.White.copy(alpha = 0.8f))
            }

            ui.items.isEmpty() -> Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                if (settings.offlineMode) {
                    Text(
                        "Offline mode is on, so only downloaded songs can play.",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scope.launch { c.settings.setOfflineMode(false); vm.refresh() } }) { Text("Go online") }
                } else {
                    Text(
                        "Nothing to recommend yet. Go online or allow access to songs on your phone, " +
                            "then listen to a few songs — your feed will adapt to your taste.",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::refresh) { Text("Try again") }
                }
            }

            else -> VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { items.getOrNull(it)?.track?.id ?: "p$it" },
                beyondViewportPageCount = 1,
            ) { page ->
                val s = items.getOrNull(page) ?: return@VerticalPager
                val isCurrent = state.current?.id == s.track.id
                FeedPage(
                    suggestion = s,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && state.isPlaying,
                    isBuffering = isCurrent && state.isBuffering,
                    progress = { pos.value.let { if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f } },
                    liked = s.track.id in favorites,
                    lyrics = lyrics?.takeIf { isCurrent && it.first == s.track.id }?.second,
                    positionMs = { pos.value.positionMs },
                    onSeek = c.player::seekTo,
                    showHint = page == 0,
                    onPlay = { armed = true; if (isCurrent) c.player.togglePlay() else playPage(page) },
                    onLike = { scope.launch { c.library.toggleFavorite(s.track) } },
                    onMore = { menuFor = s.track },
                    onArtist = { nav.navigate(Routes.artist(s.track.artist)) },
                    durationMs = { pos.value.durationMs },
                    onNext = { armed = true; scope.launch { if (page + 1 < items.size) pager.animateScrollToPage(page + 1) else playPage(page) } },
                    onPrev = { armed = true; scope.launch { if (page > 0) pager.animateScrollToPage(page - 1) else playPage(page) } },
                    onSwipeLike = {
                        if (s.track.id !in favorites) scope.launch { c.library.toggleFavorite(s.track) }
                        Toast.makeText(context, "♥ Added to Liked Songs", Toast.LENGTH_SHORT).show()
                    },
                    onSwipeDislike = {
                        vm.dislike(s.track)
                        Toast.makeText(context, "Got it. You'll see fewer songs like this.", Toast.LENGTH_SHORT).show()
                        scope.launch { if (page + 1 < items.size) pager.animateScrollToPage(page + 1) }
                    },
                )
            }
        }

        // Upar ka title + mood buttons
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ui.mood?.let { "${it.emoji} ${it.name}" } ?: "For You",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { nav.navigate(Routes.DJ) }) { Icon(Icons.Rounded.AutoAwesome, "AI DJ", tint = Color.White) }
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "New feed", tint = Color.White) }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                )
                FilterChip(
                    selected = ui.mood == null,
                    onClick = { vm.setMood(null) },
                    label = { Text("✨ For You") },
                    colors = chipColors,
                    border = null,
                )
                Moods.all.forEach { m ->
                    FilterChip(
                        selected = ui.mood == m,
                        onClick = { vm.setMood(m) },
                        label = { Text("${m.emoji} ${m.name}") },
                        colors = chipColors,
                        border = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPage(
    suggestion: Suggestion,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: () -> Float,
    liked: Boolean,
    lyrics: Lyrics?,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    showHint: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onArtist: () -> Unit,
    durationMs: () -> Long,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSwipeLike: () -> Unit,
    onSwipeDislike: () -> Unit,
) {
    val t = suggestion.track
    val spec = Sangeet.spec
    // Right swipe = like, left swipe = "aisa mat dikhao"
    var dragX by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(t.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragX > SWIPE_PX -> onSwipeLike()
                            dragX < -SWIPE_PX -> onSwipeDislike()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, d -> dragX += d },
                )
            }
            .graphicsLayer { translationX = dragX * 0.35f },
    ) {
        // Soft background: a tiny copy of the cover stretched full screen looks blurred for free
        // (a real blur on every feed page was too heavy for many phones while scrolling).
        Artwork(
            t.artworkUrl?.replace("500x500", "50x50"),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            seed = t.title,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Artwork(
                    t.artworkUrl,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(onClick = onPlay),
                    shape = RoundedCornerShape(16.dp),
                    seed = t.title,
                )
                if (!isPlaying) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBuffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                        else Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                t.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                "${t.artist} • ${t.source.label}",
                modifier = Modifier.clickable(onClick = onArtist),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) spec.accent else Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            if (lyrics != null) {
                Spacer(Modifier.height(12.dp))
                FeedLyrics(lyrics, positionMs, onSeek)
            }
            Spacer(Modifier.height(16.dp))
            if (isCurrent) FeedSeekBar(positionMs, durationMs, onSeek)
            if (dragX > SWIPE_PX / 2 || dragX < -SWIPE_PX / 2) {
                Spacer(Modifier.height(12.dp))
                Icon(
                    if (dragX > 0) Icons.Rounded.Favorite else Icons.Rounded.ThumbDown,
                    null,
                    tint = if (dragX > 0) spec.accent else Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/** Sirf yahi hissa har tick pe dobara banta hai, poora page nahi. */
@Composable
private fun FeedLyrics(lyrics: Lyrics, positionMs: () -> Long, onSeek: (Long) -> Unit) {
    LyricsView(
        lyrics,
        positionMs(),
        onSeek = onSeek,
        textColor = Color.White,
        compact = true,
        modifier = Modifier.fillMaxWidth().height(96.dp),
    )
}

private const val SWIPE_PX = 180f

/** Feed ka seek bar: sirf yahi har tick pe update hota hai. Kheencho = gaana aage/peeche. */
@Composable
private fun FeedSeekBar(positionMs: () -> Long, durationMs: () -> Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { floatState(0f) }
    val dur = durationMs().coerceAtLeast(1)
    val pos = positionMs()
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(if (dragging) (dragValue * dur).toLong() else pos), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
