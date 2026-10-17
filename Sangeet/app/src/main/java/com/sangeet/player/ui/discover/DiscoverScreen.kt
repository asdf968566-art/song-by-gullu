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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.PlaylistAdd
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.sangeet.player.ui.theme.LocalBottomBarSpace
import com.sangeet.player.ui.theme.ThemeSpec
import com.sangeet.player.ui.theme.glassCircle
import com.sangeet.player.ui.theme.glassEverywhere
import com.sangeet.player.ui.theme.neonColors
import com.sangeet.player.ui.theme.playButtonStyle
import com.sangeet.player.ui.theme.playIconColor
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance

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
    /** Songs put after a song by [follow]: their count, -1 while looking (0 = none found, it may look again). */
    private val followed = HashMap<String, Int>()

    init {
        loadMore()
        // A song from the feed that plays for 25 s brings songs like it next (report #51). Runs while the feed
        // plays, also when another tab is open.
        viewModelScope.launch {
            c.player.state.map { it.current?.id }.distinctUntilChanged().collectLatest { id ->
                if (id == null) return@collectLatest
                var listened = 0
                while (listened < FOLLOW_AFTER_S) {
                    delay(1_000)
                    if (c.player.state.value.isPlaying) listened++
                }
                val t = c.player.state.value.current?.takeIf { it.id == id } ?: return@collectLatest
                if (c.player.feedActive) follow(t)
            }
        }
    }

    /**
     * Songs like [t] (its singer, its years, its kind: [com.sangeet.player.data.recommend.RecommendationRepository.likeThis])
     * go right after it in the feed and in the queue, so the next swipe and the autoplay bring them. Moods stay as
     * they are.
     */
    fun follow(t: Track) {
        if (_ui.value.mood != null) return
        val done = followed[t.id] ?: 0
        if (done != 0) {
            if (done > 0) android.util.Log.i("Sangeet", "For You: $done songs like '${t.title}' are already next")
            return
        }
        followed[t.id] = -1
        viewModelScope.launch {
            val have = _ui.value.items.mapTo(HashSet()) { it.track.id } + c.player.state.value.queue.map { it.id }
            val more = runCatching { c.recommendations.likeThis(t, have) }.getOrDefault(emptyList())
            val now = _ui.value.items
            val at = now.indexOfFirst { it.track.id == t.id }
            val fresh = more.filter { m -> now.none { it.track.id == m.id } }
            if (fresh.isEmpty()) android.util.Log.i("Sangeet", "For You: nothing like '${t.title}' found")
            if (fresh.isEmpty() || at < 0 || _ui.value.mood != null) { followed[t.id] = 0; return@launch }
            followed[t.id] = fresh.size
            android.util.Log.i("Sangeet", "For You: ${fresh.size} songs like '${t.title}' come next")
            _ui.value = _ui.value.copy(items = now.take(at + 1) + fresh.map { Suggestion(it, "Like \"${t.title}\"") } + now.drop(at + 1))
            if (c.player.feedActive) c.player.insertAfter(t.id, fresh)
        }
    }

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
        followed.clear()
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

    /** Mood ke gaane: from playlists made for the mood, other playlists on each page (endless feed). */
    private suspend fun moodBatch(mood: Mood, have: Set<String>): List<Suggestion> {
        val page = moodPage++
        return c.online.moodTracks(mood, c.settings.current.languages, page)
            .filter { it.id !in have }
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

    val feed = feedSpec()
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
                key = { "${it}_${items.getOrNull(it)?.track?.id}" },
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
                    onLike = {
                        if (s.track.id !in favorites) vm.follow(s.track)
                        scope.launch { c.library.toggleFavorite(s.track) }
                    },
                    onMore = { menuFor = s.track },
                    onArtist = { nav.navigate(Routes.artist(s.track.artist)) },
                    durationMs = { pos.value.durationMs },
                    onNext = { armed = true; scope.launch { if (page + 1 < items.size) pager.animateScrollToPage(page + 1) else playPage(page) } },
                    onPrev = { armed = true; scope.launch { if (page > 0) pager.animateScrollToPage(page - 1) else playPage(page) } },
                    onSwipeLike = {
                        if (s.track.id !in favorites) scope.launch { c.library.toggleFavorite(s.track) }
                        vm.follow(s.track)
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
                if (feed.glassEverywhere) {
                    FeedButton(feed, onClick = { nav.navigate(Routes.DJ) }) { Icon(Icons.Rounded.AutoAwesome, "AI DJ", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(10.dp))
                    FeedButton(feed, onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "New feed", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(8.dp))
                } else {
                    IconButton(onClick = { nav.navigate(Routes.DJ) }) { Icon(Icons.Rounded.AutoAwesome, "AI DJ", tint = Color.White) }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "New feed", tint = Color.White) }
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Glass themes: the chosen chip takes the theme's color (report #51: For You looked unthemed).
                val chosen = when {
                    feed.style == com.sangeet.player.data.settings.ThemeStyle.NEON -> feed.neonColors.solid
                    feed.glassEverywhere -> feed.accent
                    else -> Color.White
                }
                val chipColors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = if (feed.glassEverywhere) 0.16f else 0.12f),
                    labelColor = Color.White,
                    selectedContainerColor = chosen,
                    selectedLabelColor = if (chosen.luminance() > 0.5f) Color.Black else Color.White,
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
    val feed = feedSpec()
    val glass = feed.glassEverywhere
    // "Add to playlist" right next to Like (a tester's report, Oct 10).
    var addTo by remember { mutableStateOf(false) }
    if (addTo) com.sangeet.player.ui.components.AddToPlaylistDialog(listOf(t)) { addTo = false }
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
        // Neon Glass: its blue-violet page over the cover, so For You looks like the rest of the app.
        if (feed.style == com.sangeet.player.data.settings.ThemeStyle.NEON) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(feed.neonColors.page.map { it.copy(alpha = 0.55f) })))
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 24.dp + LocalBottomBarSpace.current),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val coverShape = RoundedCornerShape(if (glass) 24.dp else 16.dp)
                Artwork(
                    t.artworkUrl,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        // Glass themes: the cover floats on a shadow with a bright glass rim, as in Now Playing.
                        .then(
                            if (glass) Modifier
                                .shadow(20.dp, coverShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.4f))
                                .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.45f))), coverShape)
                            else Modifier
                        )
                        .clickable(onClick = onPlay),
                    shape = coverShape,
                    seed = t.title,
                )
                if (!isPlaying) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .then(if (glass) Modifier.glassCircle(feed) else Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape))
                            .clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBuffering) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                        else Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // Add to playlist right after the song's name (report #53).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                FeedButton(feed, onClick = { addTo = true }, size = 36.dp) {
                    Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                "${t.artist} • ${t.source.label}",
                modifier = Modifier.clickable(onClick = onArtist),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            // Five controls fit a small phone (320 dp) with gaps; glass ones are real 40 dp circles (report #53: as
            // IconButtons they grew to 48 dp and overlapped).
            val icon = if (glass) 24.dp else 28.dp
            val skip = if (glass) 26.dp else 34.dp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (glass) 10.dp else 4.dp)) {
                FeedButton(feed, onClick = onLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) spec.accent else Color.White,
                        modifier = Modifier.size(icon),
                    )
                }
                FeedButton(feed, onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(skip))
                }
                // The theme's own play button (Neon's glow, Liquid Glass's colored glass…).
                val neumorphic = feed.style == com.sangeet.player.data.settings.ThemeStyle.NEUMORPHISM
                Box(
                    Modifier
                        .size(64.dp)
                        .then(if (neumorphic) Modifier.background(feed.accent, CircleShape) else Modifier.playButtonStyle(feed, 64.dp))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play/Pause",
                        tint = if (neumorphic) (if (feed.accent.luminance() > 0.5f) Color.Black else Color.White) else playIconColor(feed),
                        modifier = Modifier.size(36.dp),
                    )
                }
                FeedButton(feed, onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(skip))
                }
                FeedButton(feed, onClick = onMore) {
                    Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(icon))
                }
            }
            if (lyrics != null) {
                Spacer(Modifier.height(12.dp))
                FeedLyrics(lyrics, positionMs, onSeek)
            }
            Spacer(Modifier.height(16.dp))
            if (isCurrent) FeedSeekBar(positionMs, durationMs, onSeek, active = if (glass) feed.accent else Color.White)
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

/** Seconds a feed song plays before songs like it are put next. */
private const val FOLLOW_AFTER_S = 25

/**
 * For You is always a dark page over the song's cover, so its glass and buttons use the theme's dark look whatever
 * the light/dark setting (light glass with dark icons would vanish there).
 */
@Composable
private fun feedSpec(): ThemeSpec {
    val spec = Sangeet.spec
    return remember(spec) { spec.copy(isDark = true, onSurface = Color.White, muted = Color.White.copy(alpha = 0.7f)) }
}

/** A control of the feed; on Neon / Liquid Glass a round glass button (report #51), else a plain icon button. */
@Composable
private fun FeedButton(feed: ThemeSpec, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 40.dp, content: @Composable () -> Unit) {
    if (feed.glassEverywhere) com.sangeet.player.ui.components.GlassIconButton(onClick, size = size, spec = feed, content = content)
    else IconButton(onClick = onClick, modifier = Modifier.size(size), content = content)
}

/** Feed ka seek bar: sirf yahi har tick pe update hota hai. Tap / kheencho = gaana aage/peeche; the loaded part shows too. */
@Composable
private fun FeedSeekBar(positionMs: () -> Long, durationMs: () -> Long, onSeek: (Long) -> Unit, active: Color = Color.White) {
    val buffered by LocalAppContainer.current.player.position.collectAsStateWithLifecycle()
    com.sangeet.player.ui.components.SeekBar(
        com.sangeet.player.playback.PlaybackPosition(positionMs(), durationMs(), buffered.bufferedMs),
        onSeek,
        color = active,
        textColor = Color.White,
    )
}
