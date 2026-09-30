package com.sangeet.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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

    /** Doosre app se audio file / link khola gaya to seedha bajao. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val container = (application as SangeetApplication).container
        container.player.play(listOf(ExternalTracks.fromUri(this, uri)))
    }
}
