package com.sangeet.player.ui.settings

import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NewReleases
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
import com.sangeet.player.ui.theme.LocalBottomBarSpace

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val eq by c.equalizer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val qualities = AudioQuality.entries

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomBarSpace.current)) {
        SettingsTopBar(nav, "Settings")

        SettingsGroup("Look") {
            SettingsItem(Icons.Rounded.Palette, "Theme", "${s.theme.label} • ${s.accent.label} • ${s.darkMode.label}", onClick = {
                nav.navigate(Routes.THEMES)
            })
        }

        SettingsGroup("Song languages") { LanguageChooser() }

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
            SettingsItem(Icons.Rounded.Alarm, "Alarm", "Wake up to your songs", onClick = { nav.navigate(Routes.ALARM) })
            SettingsSwitch(Icons.Rounded.VolumeUp, "Normalize volume", "Every song plays at the same loudness", eq.normalize) {
                c.equalizer.setNormalize(it)
            }
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
            SettingsSwitch(
                Icons.Rounded.Groups,
                "Help improve suggestions",
                "Now and then, sends what you search, like, play and put in playlists, without your name, number or phone files, so suggestions learn from all listeners",
                s.shareListening,
            ) { scope.launch { c.settings.setShareListening(it) } }
            SettingsSwitch(Icons.Rounded.NotificationsActive, "New song alerts", "A notification when a singer you play most has a new song", s.newSongAlerts) {
                scope.launch { c.settings.setNewSongAlerts(it) }
            }
            SettingsSwitch(Icons.Rounded.WbSunny, "Song of the day", "One song picked for you every morning", s.songOfTheDay) {
                scope.launch { c.settings.setSongOfTheDay(it) }
            }
            SettingsSwitch(Icons.Rounded.Subtitles, "Fetch lyrics automatically", "Get lyrics from LRCLIB and save them for offline", s.autoLyrics) {
                scope.launch { c.settings.setAutoLyrics(it) }
            }
        }

        SettingsGroup("Online / Offline") {
            SettingsSwitch(Icons.Rounded.CloudOff, "Offline mode", "Only play downloads and songs on this phone. Uses no data.", s.offlineMode) {
                scope.launch { c.settings.setOfflineMode(it) }
            }
            SettingsSwitch(
                Icons.Rounded.SdStorage,
                "Save downloads to phone storage",
                if (com.sangeet.player.data.download.PhoneMusic.supported()) "Also keeps a copy in Music/Sangeet. Stays even if you uninstall the app"
                else "Needs Android 10 or newer",
                s.saveToPhone,
            ) { on ->
                if (!com.sangeet.player.data.download.PhoneMusic.supported()) return@SettingsSwitch
                scope.launch {
                    c.settings.setSaveToPhone(on)
                    if (on) {
                        val n = c.downloads.copyAllToPhone()
                        android.widget.Toast.makeText(context, if (n > 0) "Copied $n songs to Music/Sangeet" else "New downloads will also go to Music/Sangeet", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            SettingsSwitch(Icons.Rounded.Wifi, "Download on Wi-Fi only", "Downloads pause on mobile data", s.downloadOnWifiOnly) {
                scope.launch { c.settings.setDownloadOnWifiOnly(it) }
            }
            SettingsItem(Icons.Rounded.Public, "Music sources", "JioSaavn, YouTube, Audius, Jamendo, your server • Owner dashboard", onClick = {
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

        SettingsGroup("AI DJ") {
            var aiKey by remember(s.anthropicApiKey) { mutableStateOf(s.anthropicApiKey) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "The AI DJ works without a key: the built-in DJ, plus a free online AI when that's on. " +
                        "Optionally add an Anthropic API key (console.anthropic.com) to let Claude (Haiku 5.5) plan your mixes. API usage is billed to your Anthropic account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sangeet.spec.muted,
                )
                OutlinedTextField(
                    value = aiKey,
                    onValueChange = { aiKey = it },
                    label = { Text("Anthropic API key (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = {
                    scope.launch {
                        c.settings.setAnthropicApiKey(aiKey)
                        Toast.makeText(context, if (aiKey.isBlank()) "Using the built-in DJ" else "Key saved", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save") }
            }
            SettingsSwitch(
                Icons.Rounded.Psychology,
                "Free online AI",
                "Without a key, the DJ also asks a free online AI, ${com.sangeet.player.data.ai.FreeAi.NAME}, for songs. Only songs that really exist are played. It gets your request and the singers you play most",
                s.freeAi,
            ) { scope.launch { c.settings.setFreeAi(it) } }
            var geminiKey by remember(s.geminiApiKey) { mutableStateOf(s.geminiApiKey) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (com.sangeet.player.BuildConfig.GEMINI_API_KEY.isNotBlank()) "Google Gemini is on (built in). You can use your own free key instead."
                    else "Optional: a free Google Gemini key (aistudio.google.com → Get API key) makes the online AI better.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sangeet.spec.muted,
                )
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API key (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = {
                    scope.launch {
                        c.settings.setGeminiApiKey(geminiKey)
                        Toast.makeText(context, if (geminiKey.isBlank()) "Gemini key removed" else "Gemini key saved", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save Gemini key") }
            }
            SettingsItem(Icons.Rounded.AutoAwesome, "Open AI DJ", "Describe a vibe, get a playlist", onClick = { nav.navigate(Routes.DJ) })
            SettingsItem(
                Icons.Rounded.DeleteSweep,
                "Reset For You history",
                "Songs already shown can appear again (${c.recommendations.seenCount} remembered)",
                onClick = {
                    c.recommendations.clearSeen()
                    Toast.makeText(context, "For You history cleared", Toast.LENGTH_SHORT).show()
                },
            )
        }

        SettingsGroup("App update") {
            // The Google Play version is updated by Google Play.
            if (!com.sangeet.player.BuildConfig.PLAY_STORE) {
                SettingsItem(
                    Icons.Rounded.SystemUpdate,
                    "Check for updates",
                    "Current: version ${com.sangeet.player.BuildConfig.VERSION_NAME} (build ${c.updater.currentBuild})",
                    onClick = { scope.launch { c.updater.check() } },
                )
                SettingsSwitch(
                    Icons.Rounded.Autorenew,
                    "Update automatically",
                    "When there's internet, new versions download and install by themselves while you're not using the app",
                    s.autoUpdate,
                ) { scope.launch { c.settings.setAutoUpdate(it) } }
            }
            var showLog by remember { mutableStateOf(false) }
            SettingsItem(Icons.Rounded.NewReleases, "What's new", "What changed in each update", onClick = { showLog = true })
            if (showLog) {
                val log = remember { com.sangeet.player.data.WhatsNew.all(context) }
                com.sangeet.player.ui.components.WhatsNewDialog(log) { showLog = false }
            }
            if (!com.sangeet.player.BuildConfig.PLAY_STORE) {
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
        }

        SettingsGroup("Help") {
            SettingsItem(Icons.Rounded.CloudSync, "Sync with another phone", "Liked songs and playlists the same on both (Android or iPhone)", onClick = {
                nav.navigate(Routes.sync())
            })
            SettingsItem(Icons.Rounded.SyncAlt, "Move library to another phone", "Liked songs and playlists, to another Android or your iPhone", onClick = {
                scope.launch {
                    val link = com.sangeet.player.data.LibrarySync.exportLink(c.library)
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, link),
                            "Send your library",
                        )
                    )
                }
            })
            SettingsItem(Icons.Rounded.BugReport, "Report a problem", "Tell us what went wrong, sent right from here", onClick = {
                nav.navigate(Routes.REPORT)
            })
        }

        SettingsGroup("About") {
            SettingsItem(Icons.Rounded.Info, "Sangeet ${com.sangeet.player.BuildConfig.VERSION_NAME}", "Kotlin + Jetpack Compose + Media3 • Lyrics: LRCLIB")
        }
        Spacer(Modifier.height(32.dp))
    }
}
