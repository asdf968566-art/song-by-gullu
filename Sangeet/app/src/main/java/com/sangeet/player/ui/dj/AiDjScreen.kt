package com.sangeet.player.ui.dj

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.sangeet.player.AppContainer
import com.sangeet.player.data.ai.DjResult
import com.sangeet.player.data.model.Track
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.appViewModel
import com.sangeet.player.ui.components.LoadingBox
import com.sangeet.player.ui.components.TrackOptionsSheet
import com.sangeet.player.ui.components.TrackRow
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiDjViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(val working: Boolean = false, val result: DjResult? = null, val error: String? = null)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun ask(request: String) {
        val q = request.trim()
        if (q.isEmpty() || _ui.value.working) return
        _ui.value = Ui(working = true)
        viewModelScope.launch {
            _ui.value = try {
                val r = c.aiDj.make(q)
                if (r.tracks.isEmpty()) Ui(error = "No songs found for that. Try different words.")
                else {
                    // Start playing right away, like a real DJ.
                    c.player.play(r.tracks)
                    Ui(result = r)
                }
            } catch (e: Exception) {
                Ui(error = e.message ?: "Something went wrong.")
            }
        }
    }
}

private val examples = listOf(
    "Sad Punjabi songs for a night drive",
    "90s Kumar Sanu romantic hits",
    "Arijit Singh chill",
    "Bollywood party mix",
    "Lofi for studying",
    "Morning bhajan",
)

/** AI DJ: describe what you want to hear, get a playlist that starts playing. */
@Composable
fun AiDjScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val vm = appViewModel { AiDjViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val spec = Sangeet.spec
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Track?>(null) }

    menuFor?.let { TrackOptionsSheet(it, onDismiss = { menuFor = null }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = spec.onSurface) }
                Icon(Icons.Rounded.AutoAwesome, null, tint = spec.accent)
                Text("  AI DJ", style = MaterialTheme.typography.titleLarge, color = spec.onSurface)
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("What do you want to hear?") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.ask(text) }),
                trailingIcon = {
                    IconButton(onClick = { vm.ask(text) }, enabled = text.isNotBlank() && !ui.working) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Ask", tint = spec.accent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                examples.forEach { e ->
                    AssistChip(
                        onClick = { text = e; vm.ask(e) },
                        label = { Text(e) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = spec.onSurface),
                    )
                }
            }
        }

        when {
            ui.working -> item { LoadingBox() }
            ui.error != null -> item {
                Text(ui.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }

        ui.result?.let { r ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(r.plan.title.ifBlank { "Your mix" }, style = MaterialTheme.typography.headlineSmall, color = spec.onSurface)
                    if (r.plan.reply.isNotBlank()) {
                        Text(r.plan.reply, style = MaterialTheme.typography.bodyMedium, color = spec.muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { c.player.play(r.tracks) }) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text(" Play")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                c.library.createPlaylist(r.plan.title.ifBlank { "AI DJ mix" }, r.tracks)
                                Toast.makeText(context, "Saved to your Library", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Rounded.LibraryAdd, null)
                            Text(" Save")
                        }
                    }
                }
            }
            itemsIndexed(r.tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(t, onClick = { c.player.play(r.tracks, i) }, index = i, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
