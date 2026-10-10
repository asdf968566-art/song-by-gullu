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
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.sangeet.player.ui.theme.bottomBarPadding

class AiDjViewModel(private val c: AppContainer) : ViewModel() {
    /** One message of the chat: what the listener said and the DJ's answer. */
    data class Turn(val said: String, val answer: String)
    data class Ui(
        val working: Boolean = false,
        val result: DjResult? = null,
        val error: String? = null,
        val chat: List<Turn> = emptyList(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    // The chat so far: a follow-up ("aur", "sirf Arijit", "no remix") changes the last mix.
    private var intent: com.sangeet.player.data.ai.DjIntent? = null
    private val shown = LinkedHashSet<String>()
    private val history = ArrayList<String>()

    private var late: Job? = null

    fun ask(request: String) {
        val q = request.trim()
        if (q.isEmpty() || _ui.value.working) return
        _ui.value = _ui.value.copy(working = true, error = null)
        late?.cancel()
        viewModelScope.launch {
            val before = shown.toSet()
            val past = history.toList()
            var made: DjResult? = null
            _ui.value = try {
                // The built-in DJ answers at once; the online AI's songs come in while it plays (mixInAi).
                val r = c.aiDj.make(q, intent, before, past, waitForFreeAi = false)
                val chat = (_ui.value.chat + Turn(q, r.plan.reply.ifBlank { r.plan.title })).takeLast(12)
                if (r.tracks.isEmpty()) _ui.value.copy(working = false, error = "No songs found for that. Try different words.", chat = chat)
                else {
                    intent = r.intent
                    history += q
                    shown += r.tracks.map { it.id }
                    // Start playing right away, like a real DJ.
                    c.player.play(r.tracks)
                    made = r
                    Ui(result = r, chat = chat)
                }
            } catch (e: Exception) {
                _ui.value.copy(working = false, error = e.message ?: "Something went wrong.")
            }
            made?.let { r -> late = launch { mixInAi(q, r, before, past) } }
        }
    }

    /** The online AI's real songs for [r], put between the DJ's songs still to come (when that mix still plays). */
    private suspend fun mixInAi(q: String, r: DjResult, before: Set<String>, past: List<String>) {
        val picks = c.aiDj.freePicks(q, r, before, past)
        if (picks.isEmpty() || _ui.value.result !== r) return
        val ps = c.player.state.value
        val playing = ps.queue.map { it.id }.toSet()
        if (r.tracks.none { it.id in playing }) return // the listener moved on to other music
        val extra = picks.filter { it.id !in playing }.distinctBy { it.id }.take(20)
        if (extra.isEmpty()) return
        c.player.mixIntoQueue(extra)
        shown += extra.map { it.id }
        // The list on screen in the order they'll play: after the current song, an AI pick, then a DJ song…
        val at = r.tracks.indexOfFirst { it.id == ps.current?.id }.coerceAtLeast(0)
        val rest = r.tracks.drop(at + 1)
        val tracks = r.tracks.take(at + 1) +
            (0 until maxOf(extra.size, rest.size)).flatMap { n -> listOfNotNull(extra.getOrNull(n), rest.getOrNull(n)) }
        val note = " (${extra.size} picked by the online AI.)"
        val chat = _ui.value.chat.toMutableList()
        chat.lastOrNull()?.let { chat[chat.lastIndex] = it.copy(answer = it.answer + note) }
        _ui.value = _ui.value.copy(result = r.copy(tracks = tracks, usedAi = true), chat = chat)
    }

    /** Start over: the next message is a new request. */
    fun newChat() {
        late?.cancel()
        intent = null
        shown.clear()
        history.clear()
        _ui.value = Ui()
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
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
                placeholder = { Text(if (ui.chat.isEmpty()) "What do you want to hear?" else "Change it: \"only Arijit\", \"no remix\", \"more\"…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.ask(text); text = "" }),
                trailingIcon = {
                    IconButton(onClick = { vm.ask(text); text = "" }, enabled = text.isNotBlank() && !ui.working) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Ask", tint = spec.accent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        // The chat: what was said and the DJ's answers.
        if (ui.chat.isNotEmpty()) {
            items(ui.chat.size, key = { "turn_$it" }) { n ->
                val turn = ui.chat[n]
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("You: ${turn.said}", style = MaterialTheme.typography.bodyMedium, color = spec.onSurface)
                    Text("DJ: ${turn.answer}", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
                }
            }
            item {
                TextButton(onClick = { vm.newChat() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("New chat") }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (ui.result?.followUps?.takeIf { it.isNotEmpty() } ?: examples).forEach { e ->
                    AssistChip(
                        onClick = { vm.ask(e); text = "" },
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
            itemsIndexed(r.tracks, key = { i, t -> "${i}_${t.id}" }) { i, t ->
                TrackRow(t, onClick = { c.player.play(r.tracks, i) }, index = i, onMore = { menuFor = t })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
