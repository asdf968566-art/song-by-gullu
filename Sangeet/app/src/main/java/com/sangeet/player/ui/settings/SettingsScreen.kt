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
            ChoiceRow("Wi-Fi par streaming", "Wi-Fi pe data ki chinta nahi", qualities, s.wifiQuality, { it.label }) {
                scope.launch { c.settings.setWifiQuality(it) }
            }
            ChoiceRow("Mobile data par streaming", "Kam quality = kam data", qualities, s.mobileQuality, { it.label }) {
                scope.launch { c.settings.setMobileQuality(it) }
            }
            ChoiceRow("Download quality", "Offline gaane is quality mein save honge", qualities, s.downloadQuality, { it.label }) {
                scope.launch { c.settings.setDownloadQuality(it) }
            }
            SettingsItem(
                Icons.Rounded.Info,
                "Quality kaise lagti hai?",
                "Apne Navidrome/Subsonic server pe exact 128/256/320 kbps. " +
                    "Jamendo: 128 = 96 kbps, baaki = high VBR. Audius: jo source deta hai. Phone ke gaane original quality mein.",
            )
        }

        SettingsGroup("Playback") {
            SettingsItem(Icons.Rounded.Equalizer, "Equalizer & Bass boost", "Awaaz apne hisaab se", onClick = { nav.navigate(Routes.EQUALIZER) })
            SettingsSwitch(Icons.Rounded.FastForward, "Silence skip karo", "Gaanon ke beech ki khamoshi hatao", s.skipSilence) {
                scope.launch { c.settings.setSkipSilence(it) }
            }
            SettingsSwitch(Icons.Rounded.AllInclusive, "Autoplay", "Queue khatam ho to aapke track record se milte-julte gaane chalte rahenge", s.autoplay) {
                scope.launch { c.settings.setAutoplay(it) }
            }
            SettingsSwitch(Icons.Rounded.AutoAwesome, "Auto playlists", "Daily Mix, Artist Mix, On Repeat... Library mein apne aap bante aur roz update hote hain", s.autoPlaylists) {
                scope.launch {
                    c.settings.setAutoPlaylists(it)
                    if (it) runCatching { c.recommendations.syncAutoPlaylists(force = true) }
                }
            }
            SettingsSwitch(Icons.Rounded.Subtitles, "Lyrics apne aap lao", "Online LRCLIB se lyrics, phir offline save", s.autoLyrics) {
                scope.launch { c.settings.setAutoLyrics(it) }
            }
        }

        SettingsGroup("Online / Offline") {
            SettingsSwitch(Icons.Rounded.CloudOff, "Offline mode", "Sirf downloaded aur phone ke gaane, data bilkul nahi", s.offlineMode) {
                scope.launch { c.settings.setOfflineMode(it) }
            }
            SettingsSwitch(Icons.Rounded.Wifi, "Sirf Wi-Fi pe download", "Mobile data pe download ruke rahenge", s.downloadOnWifiOnly) {
                scope.launch { c.settings.setDownloadOnWifiOnly(it) }
            }
            SettingsItem(Icons.Rounded.Public, "Music sources", "JioSaavn, YouTube, Audius, Jamendo, apna server • Bhasha", onClick = {
                nav.navigate(Routes.SOURCES)
            })
        }

        SettingsGroup("Storage") {
            SettingsItem(Icons.Rounded.DeleteSweep, "History saaf karo", "Recently played khaali ho jayega", onClick = {
                scope.launch {
                    c.library.clearHistory()
                    Toast.makeText(context, "History saaf", Toast.LENGTH_SHORT).show()
                }
            })
        }

        SettingsGroup("App update") {
            SettingsItem(
                Icons.Rounded.SystemUpdate,
                "Update check karo",
                "Abhi: version ${com.sangeet.player.BuildConfig.VERSION_NAME} (build ${c.updater.currentBuild})",
                onClick = { scope.launch { c.updater.check() } },
            )
            var token by remember(s.githubToken) { mutableStateOf(s.githubToken) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Repo public hai to token ki zaroorat nahi. Sirf agar repo private karo, tab yahan " +
                        "read-only GitHub token daalo (Contents = Read-only).",
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
                }) { Text("Save & update check karo") }
            }
        }

        SettingsGroup("About") {
            SettingsItem(Icons.Rounded.Info, "Sangeet ${com.sangeet.player.BuildConfig.VERSION_NAME}", "Kotlin + Jetpack Compose + Media3 • Lyrics: LRCLIB")
        }
        Spacer(Modifier.height(32.dp))
    }
}
