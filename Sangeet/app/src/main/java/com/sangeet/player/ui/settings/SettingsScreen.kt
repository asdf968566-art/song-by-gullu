package com.sangeet.player.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Key
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sangeet.player.ui.theme.Sangeet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val qualities = AudioQuality.entries

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTopBar(nav, "Settings")

        SettingsGroup("Look") {
            SettingsItem(Icons.Rounded.Palette, "Theme", "${s.theme.label} • ${s.accent.label} • ${s.darkMode.label}", onClick = {
                nav.navigate(Routes.THEMES)
            })
        }

        SettingsGroup("Audio quality") {
            ChoiceRow("Wi-Fi streaming", "Quality on Wi-Fi", qualities, s.wifiQuality, { it.label }) {
                scope.launch { c.settings.setWifiQuality(it) }
            }
            ChoiceRow("Mobile data streaming", "Lower quality uses less data", qualities, s.mobileQuality, { it.label }) {
                scope.launch { c.settings.setMobileQuality(it) }
            }
            ChoiceRow("Download quality", "Quality for downloaded songs", qualities, s.downloadQuality, { it.label }) {
                scope.launch { c.settings.setDownloadQuality(it) }
            }
            SettingsItem(
                Icons.Rounded.Info,
                "How quality works",
                "Your Navidrome/Subsonic server: exactly 128/256/320 kbps. " +
                    "Jamendo: 128 = 96 kbps, others = high VBR. Audius: whatever the source provides. Songs on your phone play in original quality.",
            )
        }

        SettingsGroup("Playback") {
            SettingsItem(Icons.Rounded.Equalizer, "Equalizer & Bass boost", "Fine-tune your sound", onClick = { nav.navigate(Routes.EQUALIZER) })
            SettingsSwitch(Icons.Rounded.FastForward, "Skip silence", "Remove silent gaps between songs", s.skipSilence) {
                scope.launch { c.settings.setSkipSilence(it) }
            }
            ChoiceRow("Crossfade", "Smooth fade between songs", listOf(0, 3, 5, 8), s.crossfadeSec, { if (it == 0) "Off" else "${it}s" }) {
                scope.launch { c.settings.setCrossfadeSec(it) }
            }
            SettingsSwitch(Icons.Rounded.MusicNote, "Hook preview in For You", "Songs in the feed start near the chorus", s.hookPreview) {
                scope.launch { c.settings.setHookPreview(it) }
            }
            SettingsSwitch(Icons.Rounded.Headphones, "Resume on headphones", "Continue playing when headphones or Bluetooth reconnect", s.headphoneResume) {
                scope.launch { c.settings.setHeadphoneResume(it) }
            }
            SettingsSwitch(Icons.Rounded.CloudDownload, "Smart downloads", "Download liked songs and your Daily Mix on Wi-Fi while charging", s.smartDownloads) {
                scope.launch { c.settings.setSmartDownloads(it) }
            }
            SettingsSwitch(Icons.Rounded.AllInclusive, "Autoplay", "When your queue ends, keep playing similar songs", s.autoplay) {
                scope.launch { c.settings.setAutoplay(it) }
            }
            SettingsSwitch(Icons.Rounded.AutoAwesome, "Auto playlists", "Daily Mix, Artist Mix, On Repeat and more, added to your library and updated daily", s.autoPlaylists) {
                scope.launch {
                    c.settings.setAutoPlaylists(it)
                    if (it) runCatching { c.recommendations.syncAutoPlaylists(force = true) }
                }
            }
            SettingsSwitch(Icons.Rounded.Subtitles, "Fetch lyrics automatically", "Get lyrics from LRCLIB and save them for offline", s.autoLyrics) {
                scope.launch { c.settings.setAutoLyrics(it) }
            }
        }

        SettingsGroup("Online / Offline") {
            SettingsSwitch(Icons.Rounded.CloudOff, "Offline mode", "Only play downloads and songs on this phone. Uses no data.", s.offlineMode) {
                scope.launch { c.settings.setOfflineMode(it) }
            }
            SettingsSwitch(Icons.Rounded.Wifi, "Download on Wi-Fi only", "Downloads pause on mobile data", s.downloadOnWifiOnly) {
                scope.launch { c.settings.setDownloadOnWifiOnly(it) }
            }
            SettingsItem(Icons.Rounded.Public, "Music sources", "JioSaavn, YouTube, Audius, Jamendo, your server • Languages", onClick = {
                nav.navigate(Routes.SOURCES)
            })
        }

        SettingsGroup("Storage") {
            SettingsItem(Icons.Rounded.DeleteSweep, "Clear history", "Clears your recently played list", onClick = {
                scope.launch {
                    c.library.clearHistory()
                    Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                }
            })
        }

        SettingsGroup("App update") {
            SettingsItem(
                Icons.Rounded.SystemUpdate,
                "Check for updates",
                "Current: version ${com.sangeet.player.BuildConfig.VERSION_NAME} (build ${c.updater.currentBuild})",
                onClick = { scope.launch { c.updater.check() } },
            )
            var token by remember(s.githubToken) { mutableStateOf(s.githubToken) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "No token is needed while the repo is public. If you make the repo private, enter a " +
                        "read-only GitHub token here (Contents = Read-only).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sangeet.spec.muted,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub token (optional)") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Key, null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = {
                    scope.launch {
                        c.settings.setGithubToken(token)
                        c.updater.check()
                    }
                }) { Text("Save & check for updates") }
            }
        }

        SettingsGroup("About") {
            SettingsItem(Icons.Rounded.Info, "Sangeet ${com.sangeet.player.BuildConfig.VERSION_NAME}", "Kotlin + Jetpack Compose + Media3 • Lyrics: LRCLIB")
        }
        Spacer(Modifier.height(32.dp))
    }
}
