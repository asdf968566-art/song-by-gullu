package com.sangeet.player

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
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                SangeetTheme(settings) {
                    SangeetRoot()
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
                    }
                } else {
                    container.player.play(listOf(ExternalTracks.fromUri(this, uri)))
                }
            }
        }
    }
}
