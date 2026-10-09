package com.sangeet.player.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.alarm.SongAlarm
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.bottomBarPadding
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/** Settings → Alarm: wake up to your liked songs, a playlist or a For You mix. */
@Composable
fun AlarmScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec
    var setup by remember { mutableStateOf(SongAlarm.get(context)) }
    val playlists by c.library.playlists.collectAsStateWithLifecycle(emptyList())
    fun save(s: SongAlarm.Setup) { setup = s; SongAlarm.set(context, s) }
    val time = String.format(Locale.US, "%02d:%02d", setup.hour, setup.minute)
    val next = SongAlarm.next(setup)?.let { SimpleDateFormat("EEE, d MMM 'at' HH:mm", Locale.US).format(it.time) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomBarPadding())) {
        SettingsTopBar(nav, "Alarm")
        SettingsGroup("Wake up to your songs") {
            SettingsSwitch(Icons.Rounded.Alarm, "Alarm", if (next != null) "Rings $next" else "Off", setup.on) { save(setup.copy(on = it)) }
            SettingsItem(Icons.Rounded.Schedule, "Time", "$time every day", onClick = {
                TimePickerDialog(context, { _, h, m -> save(setup.copy(hour = h, minute = m, on = true)) },
                    setup.hour, setup.minute, DateFormat.is24HourFormat(context)).show()
            })
        }
        SettingsGroup("What plays") {
            val pick: (String) -> Unit = { save(setup.copy(what = it)) }
            AlarmOption(Icons.Rounded.Favorite, "Liked songs", "Shuffled", "liked", setup.what, pick)
            AlarmOption(Icons.Rounded.AutoAwesome, "For You mix", "New songs you'll like (needs internet)", "foryou", setup.what, pick)
            playlists.filter { it.trackCount > 0 }.take(30).forEach { p ->
                AlarmOption(Icons.AutoMirrored.Rounded.QueueMusic, p.name, "${p.trackCount} songs", "playlist:${p.id}", setup.what, pick)
            }
        }
        SettingsGroup("Try it") {
            SettingsItem(Icons.Rounded.PlayArrow, "Play the alarm's songs now", "Without internet, only downloaded songs play", onClick = {
                scope.launch {
                    if (!SongAlarm.play(context)) Toast.makeText(context, "Like some songs or download a few first", Toast.LENGTH_SHORT).show()
                }
            })
        }
        Text(
            "Keep the phone's media volume up. It plays even when the screen is off.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.muted,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun AlarmOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, what: String, chosen: String, onPick: (String) -> Unit,
) = SettingsItem(icon, title, subtitle, onClick = { onPick(what) }) {
    if (chosen == what) Icon(Icons.Rounded.Check, "Chosen", tint = Sangeet.spec.accent)
}
