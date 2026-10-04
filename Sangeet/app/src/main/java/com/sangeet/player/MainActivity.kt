package com.sangeet.player

import com.sangeet.player.data.CrashReporter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import com.sangeet.player.data.settings.AppSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.sangeet.player.data.model.SourceType
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sangeet.player.data.ExternalTracks
import com.sangeet.player.ui.LocalAppContainer
import com.sangeet.player.ui.SangeetRoot
import com.sangeet.player.ui.theme.SangeetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SangeetApplication).container
        container.player.connect()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Only the theme settings: changing any other setting must not rebuild the whole app.
            val themeOnly = remember {
                container.settings.settings
                    .map { AppSettings(theme = it.theme, darkMode = it.darkMode, accent = it.accent) }
                    .distinctUntilChanged()
            }
            val cur = container.settings.current
            val settings by themeOnly.collectAsStateWithLifecycle(AppSettings(theme = cur.theme, darkMode = cur.darkMode, accent = cur.accent))
            CompositionLocalProvider(LocalAppContainer provides container) {
                SangeetTheme(settings) {
                    SangeetRoot()
                    // Crashed last time: offer to send the report (only then, never otherwise).
                    var crash by remember { mutableStateOf(CrashReporter.pendingCrash(this@MainActivity)) }
                    if (crash != null) {
                        AlertDialog(
                            onDismissRequest = { CrashReporter.clear(this@MainActivity); crash = null },
                            title = { Text("Sangeet closed unexpectedly") },
                            text = { Text("Send a report so it can be fixed? It opens GitHub with the error details.") },
                            confirmButton = { TextButton(onClick = { CrashReporter.send(this@MainActivity); crash = null }) { Text("Send report") } },
                            dismissButton = { TextButton(onClick = { CrashReporter.clear(this@MainActivity); crash = null }) { Text("Not now") } },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * - Audio file / link khola (VIEW) -> seedha bajao (YouTube link bhi).
     * - YouTube app se "Share -> Sangeet" (SEND) -> wo gaana bajao.
     * - sangeet://play?q=kesariya&source=jiosaavn -> search karke pehla gaana bajao.
     */
    private fun handleIntent(intent: Intent?) {
        val container = (application as SangeetApplication).container
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                ExternalTracks.fromSharedText(text)?.let { container.player.play(listOf(it)) }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme == "sangeet" && uri.host == "play") {
                    val q = uri.getQueryParameter("q") ?: return
                    val type = uri.getQueryParameter("source")?.let { name ->
                        SourceType.entries.firstOrNull { it.name.equals(name, true) }
                    }
                    lifecycleScope.launch {
                        val results = runCatching {
                            if (type != null) container.online.source(type)?.search(q, container.settings.current).orEmpty()
                            else container.online.searchAll(q)
                        }.onFailure { android.util.Log.w("Sangeet", "deep link search fail ($type, $q)", it) }
                            .getOrDefault(emptyList())
                        android.util.Log.i("Sangeet", "deep link $type '$q': ${results.size} results, first=${results.firstOrNull()?.title}")
                        if (results.isNotEmpty()) container.player.play(results.take(20))
                        // &download=1: also download the first result (used by the app check on CI).
                        if (uri.getQueryParameter("download") == "1") results.firstOrNull()?.let { container.downloads.download(it) }
                    }
                } else {
                    container.player.play(listOf(ExternalTracks.fromUri(this, uri)))
                }
            }
        }
    }
}
