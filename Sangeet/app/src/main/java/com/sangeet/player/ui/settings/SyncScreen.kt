package com.sangeet.player.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sangeet.player.data.CloudSync
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import kotlinx.coroutines.launch

/** Settings → Sync with another phone: one sync code keeps liked songs and playlists the same on both. */
@Composable
fun SyncScreen(nav: NavController, joinCode: String? = null) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    var code by remember { mutableStateOf(CloudSync.code(context)) }
    var busy by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(joinCode.orEmpty()) }
    var status by remember { mutableStateOf("") }
    fun run(what: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        status = what
        scope.launch {
            status = runCatching { block() }.getOrElse { "Didn't work: ${it.message ?: "no internet?"}" }
            code = CloudSync.code(context)
            busy = false
        }
    }
    // Opened from a sync link: join at once.
    LaunchedEffect(joinCode) {
        if (!joinCode.isNullOrBlank() && code != joinCode) run("Joining…") {
            val (l, p) = CloudSync.join(context, c.library, joinCode)
            "Synced: $l liked songs, $p playlists on both phones"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomBarPadding())) {
        SettingsTopBar(nav, "Sync with another phone")
        Text(
            "Liked songs and playlists stay the same on both phones (Android or iPhone). No account needed.",
            style = MaterialTheme.typography.bodyMedium, color = spec.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val now = code
        if (now == null) {
            SettingsGroup("This phone first") {
                SettingsItem(Icons.Rounded.CloudSync, "Start sync", "Makes a link to open on your other phone", onClick = {
                    run("Starting…") { CloudSync.start(context, c.library); "Sync is on. Now open the link on your other phone." }
                })
            }
            SettingsGroup("Or join your other phone") {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it }, singleLine = true,
                        label = { Text("Sync link or code") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        run("Joining…") { val (l, p) = CloudSync.join(context, c.library, typed); "Synced: $l liked songs, $p playlists on both phones" }
                    }, enabled = typed.isNotBlank()) { Text("Join") }
                }
            }
        } else {
            SettingsGroup("Sync is on") {
                SettingsItem(Icons.Rounded.Share, "Send the sync link", "Open it on your other phone (Android or iPhone)", onClick = {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Sangeet sync: ${CloudSync.link(now)}"),
                        "Send sync link",
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                SettingsItem(Icons.Rounded.Link, "Code", now)
                SettingsItem(Icons.Rounded.Sync, "Sync now", if (CloudSync.lastSync(context) > 0) "Also happens by itself after changes" else null, onClick = {
                    run("Syncing…") {
                        val r = CloudSync.sync(context, c.library) ?: return@run "This sync code doesn't work any more. Stop and start again."
                        "Synced: ${r.first} liked songs, ${r.second} playlists"
                    }
                })
                SettingsItem(Icons.Rounded.LinkOff, "Stop syncing", "This phone keeps its songs", onClick = {
                    CloudSync.stop(context); code = null; status = "Sync stopped on this phone"
                    Toast.makeText(context, "Sync stopped", Toast.LENGTH_SHORT).show()
                })
            }
        }
        if (status.isNotBlank()) Text(status, color = spec.onSurface, modifier = Modifier.padding(20.dp))
        Text(
            "The library is kept on restful-api.dev, a free online store, under the long code; only phones with the link can open it.",
            style = MaterialTheme.typography.bodySmall, color = spec.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
