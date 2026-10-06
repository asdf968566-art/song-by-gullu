package com.sangeet.player.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sangeet.player.data.lyrics.Lyrics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

/** Synced lyrics: chal rahi line highlight hoti hai aur apne aap scroll hota hai. Line dabao = wahan seek. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsView(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Long-press a line: share it as a picture. */
    onShareLine: ((String) -> Unit)? = null,
) {
    if (!lyrics.isSynced) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                lyrics.plain ?: "",
                color = textColor,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val lines = lyrics.lines
    val current = remember(positionMs, lines) {
        lines.indexOfLast { it.timeMs <= positionMs + 300 }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        // Chalti line screen ke upar-beech mein rahe (Resso jaisa)
        val vh = listState.layoutInfo.viewportSize.height
        listState.animateScrollToItem(current, scrollOffset = -(vh / 3))
    }

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (compact) 8.dp else 120.dp),
        userScrollEnabled = !compact,
    ) {
        itemsIndexed(lines) { i, line ->
            val color by animateColorAsState(
                when {
                    i == current -> textColor
                    i < current -> textColor.copy(alpha = 0.55f)
                    else -> textColor.copy(alpha = 0.3f)
                },
                label = "lyricColor",
            )
            Text(
                line.text.ifBlank { "♪" },
                color = color,
                style = when {
                    compact -> MaterialTheme.typography.titleMedium
                    i == current -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onSeek(line.timeMs) },
                        onLongClick = onShareLine?.let { share -> { if (line.text.isNotBlank()) share(line.text) } },
                    )
                    .padding(vertical = if (compact) 4.dp else 8.dp),
            )
        }
    }
}
