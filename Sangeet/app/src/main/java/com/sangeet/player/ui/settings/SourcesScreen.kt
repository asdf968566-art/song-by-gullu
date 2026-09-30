package com.sangeet.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.sangeet.player.data.Categories
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.theme.Sangeet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SourcesScreen(nav: NavController) {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val spec = Sangeet.spec

    var ytKey by remember(s.youtubeApiKey) { mutableStateOf(s.youtubeApiKey) }
    var ytStatus by remember { mutableStateOf<String?>(null) }
    var jamendoId by remember(s.jamendoClientId) { mutableStateOf(s.jamendoClientId) }
    var url by remember(s.subsonicUrl) { mutableStateOf(s.subsonicUrl) }
    var user by remember(s.subsonicUser) { mutableStateOf(s.subsonicUser) }
    var password by remember { mutableStateOf("") }
    var jamendoStatus by remember { mutableStateOf<String?>(null) }
    var serverStatus by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTopBar(nav, "Music sources")

        Text(
            "Sab gaane app ke andar hi bajte hain aur download ho sakte hain. Koi bahar ka link nahi khulta.",
            style = MaterialTheme.typography.bodyMedium,
            color = spec.muted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        SettingsGroup("Aapki bhasha") {
            Text(
                "In bhashaon ke gaane feed, Home aur suggestions mein pehle aayenge.",
                style = MaterialTheme.typography.bodySmall,
                color = spec.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Categories.languages.forEach { lang ->
                    val on = lang in s.languages
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) s.languages - lang else s.languages + lang
                            if (next.isNotEmpty()) scope.launch { c.settings.setLanguages(next) }
                        },
                        label = { Text(lang.replaceFirstChar(Char::uppercase)) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = spec.accent,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        SettingsGroup("JioSaavn (Hindi, Punjabi, Bollywood)") {
            SettingsSwitch(
                Icons.Rounded.LibraryMusic,
                "JioSaavn on",
                "Crore se zyada Indian gaane, 320 kbps tak. Koi key nahi.",
                s.jiosaavnEnabled,
            ) { scope.launch { c.settings.setJioSaavnEnabled(it) } }
        }

        SettingsGroup("YouTube / YouTube Music") {
            SettingsSwitch(
                Icons.Rounded.SmartDisplay,
                "YouTube on",
                "Har gaana jo YouTube pe hai. Bina key ke bhi chalta hai (NewPipe).",
                s.youtubeEnabled,
            ) { scope.launch { c.settings.setYouTubeEnabled(it) } }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Optional: YouTube Data API v3 key (free). Isse search aur India ke trending music ki " +
                        "details official API se aati hain. Key kaise lein:\n" +
                        "1. console.cloud.google.com kholo → naya project banao\n" +
                        "2. APIs & Services → Library → \"YouTube Data API v3\" → Enable\n" +
                        "3. Credentials → Create credentials → API key → copy karke yahan daalo\n" +
                        "Free quota: 10,000 units/din (search = 100, trending = 1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.muted,
                )
                OutlinedTextField(
                    value = ytKey,
                    onValueChange = { ytKey = it },
                    label = { Text("YouTube API key (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            c.settings.setYouTubeApiKey(ytKey)
                            ytStatus = "Check kar rahe hain…"
                            ytStatus = testSource(c, SourceType.YOUTUBE) { it.youtubeApiKey == ytKey.trim() }
                        }
                    }) { Text("Save & test") }
                    if (s.youtubeApiKey.isNotBlank()) {
                        OutlinedButton(onClick = { scope.launch { c.settings.setYouTubeApiKey(""); ytKey = "" } }) { Text("Hatao") }
                    }
                }
                ytStatus?.let { Text(it, color = spec.accent, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SettingsGroup("Audius (free, koi key nahi)") {
            SettingsSwitch(
                Icons.Rounded.Radio,
                "Audius on",
                "Lakhon independent artists ke gaane. Turant chalta hai.",
                s.audiusEnabled,
            ) { scope.launch { c.settings.setAudiusEnabled(it) } }
        }

        SettingsGroup("Jamendo (free Creative Commons music)") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "devportal.jamendo.com pe free account banao → 'client_id' copy karke yahan daalo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.muted,
                )
                OutlinedTextField(
                    value = jamendoId,
                    onValueChange = { jamendoId = it },
                    label = { Text("Jamendo client_id") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            c.settings.setJamendoClientId(jamendoId)
                            jamendoStatus = "Check kar rahe hain…"
                            jamendoStatus = testSource(c, SourceType.JAMENDO) { it.jamendoClientId == jamendoId.trim() }
                        }
                    }) { Text("Save & test") }
                    if (s.jamendoClientId.isNotBlank()) {
                        OutlinedButton(onClick = { scope.launch { c.settings.setJamendoClientId(""); jamendoId = "" } }) { Text("Hatao") }
                    }
                }
                jamendoStatus?.let { Text(it, color = spec.accent, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SettingsGroup("Apna server — Navidrome / Subsonic") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Apne PC ke gaane phone pe stream karo. Yahan 128/256/320 kbps bilkul exact milta hai. " +
                        "Example URL: http://192.168.1.5:4533",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.muted,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Server URL") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Username") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (s.subsonicConfigured) "Password (badalna ho to hi)" else "Password") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Password save nahi hota — sirf uska secure token (md5 + salt).",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            c.settings.setSubsonic(url, user, password)
                            password = ""
                            serverStatus = "Check kar rahe hain…"
                            serverStatus = testSource(c, SourceType.SUBSONIC) { it.subsonicUser == user.trim() && it.subsonicConfigured }
                        }
                    }) { Text("Save & test") }
                    if (s.subsonicConfigured) {
                        OutlinedButton(onClick = { scope.launch { c.settings.clearSubsonic(); serverStatus = null } }) { Text("Hatao") }
                    }
                }
                serverStatus?.let { Text(it, color = spec.accent, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private suspend fun testSource(
    c: com.sangeet.player.AppContainer,
    type: SourceType,
    ready: (com.sangeet.player.data.settings.AppSettings) -> Boolean,
): String {
    val src = c.online.source(type) ?: return "Source nahi mila"
    // Naya setting DataStore se aane tak ruko.
    val settings = withTimeoutOrNull(3000) { c.settings.settings.first(ready) } ?: c.settings.settings.value
    if (!src.isEnabled(settings)) return "Details adhoori hain"
    return try {
        val n = src.trending(settings).size
        if (n > 0) "✓ Jud gaya! $n gaane mile." else "Jud gaya, par koi gaana nahi mila."
    } catch (e: Exception) {
        "✗ Error: ${e.message}"
    }
}
