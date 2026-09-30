package com.sangeet.player.ui.discover

import android.os.Build
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
import androidx.compose.ui.draw.blur
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
import com.sangeet.player.data.model.Track
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
    data class Ui(val items: List<Suggestion> = emptyList(), val loading: Boolean = true, val exhausted: Boolean = false)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var loadingMore = false

    init { loadMore() }

    fun loadMore() {
        if (loadingMore || _ui.value.exhausted) return
        loadingMore = true
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val have = _ui.value.items.mapTo(HashSet()) { it.track.id }
            val more = runCatching { c.recommendations.suggestions(limit = 25, exclude = have) }.getOrDefault(emptyList())
            _ui.value = Ui(items = _ui.value.items + more, loading = false, exhausted = more.isEmpty())
            // Feed chal raha hai to naye gaane queue ke aakhir mein bhi jodo.
            if (more.isNotEmpty() && c.player.feedActive) c.player.addToQueue(more.map { it.track })
            loadingMore = false
        }
    }

    fun refresh() {
        _ui.value = Ui()
        loadMore()
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
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val items by rememberUpdatedState(ui.items)
    val pager = rememberPagerState { items.size }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    fun playPage(page: Int) {
        val list = vm.ui.value.items
        val t = list.getOrNull(page)?.track ?: return
        val ps = c.player.state.value
        if (ps.current?.id == t.id) return
        val inQueue = if (c.player.feedActive) ps.queue.indexOfFirst { it.id == t.id } else -1
        if (inQueue >= 0) c.player.skipTo(inQueue) else c.player.play(list.map { it.track }, page, fromFeed = true)
    }

    // Page par rukte hi wahi gaana bajao. Pehli baar tab khulne par kuch aur baj raha ho to use mat roko.
    LaunchedEffect(pager, ui.items.isNotEmpty()) {
        if (vm.ui.value.items.isEmpty()) return@LaunchedEffect
        var armed = c.player.feedActive || !c.player.state.value.isPlaying
        var first = true
        snapshotFlow { pager.settledPage }.collect { page ->
            if (first) {
                first = false
                if (!armed) return@collect
            }
            armed = true
            playPage(page)
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
                Text("Aapke liye gaane chun rahe hain…", color = Color.White.copy(alpha = 0.8f))
            }

            ui.items.isEmpty() -> Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Abhi suggest karne ke liye kuch nahi mila. Internet on karo ya phone ke gaane allow karo, " +
                        "aur kuch gaane suno — feed aapke hisaab se banega.",
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::refresh) { Text("Dobara try karo") }
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
                    progress = if (isCurrent && state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                    liked = s.track.id in favorites,
                    showHint = page == 0,
                    onPlay = { if (isCurrent) c.player.togglePlay() else playPage(page) },
                    onLike = { scope.launch { c.library.toggleFavorite(s.track) } },
                    onMore = { menuFor = s.track },
                )
            }
        }

        // Upar ka title
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Discover", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Naya feed", tint = Color.White) }
        }
    }
}

@Composable
private fun FeedPage(
    suggestion: Suggestion,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    liked: Boolean,
    showHint: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
) {
    val t = suggestion.track
    val spec = Sangeet.spec
    Box(Modifier.fillMaxSize()) {
        // Peeche dhundhla cover (Android 12+ pe blur, purane phones pe halka)
        Artwork(
            t.artworkUrl,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp) else Modifier),
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
                        .widthIn(max = 320.dp)
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
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = spec.accent, modifier = Modifier.size(16.dp))
                Text("  ${suggestion.reason}", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (liked) spec.accent else Color.White,
                        modifier = Modifier.size(30.dp),
                    )
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
                IconButton(onClick = onMore) {
                    Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isCurrent) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
            }
            if (showHint) {
                Spacer(Modifier.height(20.dp))
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.6f))
                Text("Upar scroll karo — agla gaana", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
