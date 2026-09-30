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
            SettingsItem(Icons.Rounded.Public, "Music sources", "Audius, Jamendo, apna server (Navidrome/Subsonic)", onClick = {
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

        SettingsGroup("About") {
            SettingsItem(Icons.Rounded.Info, "Sangeet 1.0", "Kotlin + Jetpack Compose + Media3 • Lyrics: LRCLIB")
        }
        Spacer(Modifier.height(32.dp))
    }
}
