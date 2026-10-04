package com.sangeet.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sangeet.player.ui.theme.Sangeet

/** Cover art. Na mile to rangeen gradient + note icon. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    seed: String = url ?: "",
) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    val spec = Sangeet.spec
    val m = (if (size != null) modifier.size(size) else modifier).clip(shape)
    Box(m, contentAlignment = Alignment.Center) {
        if (failed) {
            val hue = (seed.hashCode() and 0x7fffffff) % 360
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.hsv(hue.toFloat(), 0.55f, 0.55f), spec.accent.copy(alpha = 0.8f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f))
            }
        } else {
            AsyncImage(
                // Small thumbnails (lists, mini player) don't need the 500 px cover: less data, faster scrolling.
                model = if (size != null && size <= 72.dp) url?.replace("500x500", "150x150") else url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    }
}
