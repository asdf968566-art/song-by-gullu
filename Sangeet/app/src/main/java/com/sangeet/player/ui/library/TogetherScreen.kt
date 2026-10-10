package com.sangeet.player.ui.library

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.settings.SettingsGroup
import com.sangeet.player.ui.settings.SettingsItem
import com.sangeet.player.ui.settings.SettingsTopBar
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding

/** Library → Listen together: start a room or join a friend's, then both phones play the same song. */
@Composable
fun TogetherScreen(nav: NavController, joinCode: String? = null) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val spec = Sangeet.spec
    val room by c.together.room.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf(joinCode.orEmpty()) }
    var wrong by remember { mutableStateOf(false) }
    // Opened from a friend's link: join at once.
    LaunchedEffect(joinCode) { if (!joinCode.isNullOrBlank()) c.together.join(joinCode) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomBarPadding())) {
        SettingsTopBar(nav, "Listen together")
        Text(
            "Play the same song at the same moment on your friends' phones (Android or iPhone).",
            style = MaterialTheme.typography.bodyMedium, color = spec.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val r = room
        if (r == null) {
            SettingsGroup("Start") {
                SettingsItem(Icons.Rounded.Groups, "Start a room", "Your friends hear what you play", onClick = { c.together.start() })
            }
            SettingsGroup("Or join a friend") {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it; wrong = false }, singleLine = true,
                        label = { Text("Room code or link") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        isError = wrong,
                    )
                    TextButton(onClick = { wrong = !c.together.join(typed) }, enabled = typed.isNotBlank()) { Text("Join") }
                    if (wrong) Text("A room code has 6 letters and numbers", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            SettingsGroup(if (r.host) "Your room" else "In a friend's room") {
                SettingsItem(Icons.Rounded.Link, "Room code", r.code)
                if (r.host) {
                    SettingsItem(Icons.Rounded.Share, "Invite friends", "Send them the link", onClick = {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Listen with me on Sangeet: ${c.together.link(r.code)}"),
                            "Invite",
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                }
                SettingsItem(Icons.Rounded.LinkOff, "Leave", null, onClick = { c.together.leave() })
            }
            if (r.note.isNotBlank()) Text(r.note, color = spec.onSurface, modifier = Modifier.padding(20.dp))
        }
    }
}
