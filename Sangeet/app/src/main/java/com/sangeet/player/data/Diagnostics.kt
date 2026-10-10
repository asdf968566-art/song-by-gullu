package com.sangeet.player.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.sangeet.player.SangeetApplication

/**
 * Phone details for app reports (owner, Oct 10: "kisi specific phone me problem aaye to us problem ke liye data le
 * sakta hai, koi sensitive mt lena"). Only how the phone and the app are set up, so a problem on one phone can be
 * found (like the status bar on a LAVA phone). Never songs, playlists, searches, names, accounts, files or location:
 * reports end up in a public repo.
 */
object Diagnostics {
    // Filled in by the UI as it draws (SangeetRoot).
    @Volatile var statusBarDp = -1
    @Volatile var navBarDp = -1
    @Volatile var cutoutTopDp = -1
    private val screens = ArrayDeque<String>()
    private val icons = LinkedHashMap<String, Boolean>()

    /** A screen was opened: its route pattern only (e.g. "movie?title={title}"), never what was in it. */
    @Synchronized
    fun opened(route: String?) {
        val r = route?.substringBefore('?') ?: return
        if (screens.lastOrNull() == r) return
        screens.addLast(r)
        while (screens.size > 6) screens.removeFirst()
    }

    /** The status bar icon colour the app set on [route] (light icons = for a dark top). */
    @Synchronized
    fun barIcons(route: String?, light: Boolean) {
        val r = route?.substringBefore('?') ?: "start"
        icons.remove(r)
        icons[r] = light
        while (icons.size > 6) icons.remove(icons.keys.first())
    }

    @Synchronized
    fun text(context: Context): String = buildString {
        val res = context.resources
        val conf = res.configuration
        val night = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val nav = when (runCatching { android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1) }.getOrDefault(-1)) {
            0 -> "3 buttons"
            1 -> "2 buttons"
            2 -> "gestures"
            else -> "unknown"
        }
        appendLine("**Phone details**")
        appendLine("- Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND} ${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
        appendLine(
            "- Screen: ${conf.screenWidthDp}×${conf.screenHeightDp} dp, density ${res.displayMetrics.density}, " +
                "font ${conf.fontScale}×, phone in ${if (night) "dark" else "light"} mode, " +
                if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait",
        )
        appendLine("- Bars: status bar $statusBarDp dp, camera cutout $cutoutTopDp dp, navigation bar $navBarDp dp ($nav)")
        if (icons.isNotEmpty()) {
            appendLine("- Status bar icons: " + icons.entries.joinToString(", ") { "${it.key} ${if (it.value) "dark" else "light"}" })
        }
        val app = context.applicationContext as? SangeetApplication
        app?.container?.let { c ->
            val s = c.settings.current
            appendLine(
                "- App: theme ${s.theme.label}, ${s.darkMode.label.lowercase()} mode, accent ${s.accent.label}, " +
                    "offline mode ${if (s.offlineMode) "on" else "off"}, YouTube ${if (s.youtubeEnabled) "on" else "off"}",
            )
            val playing = c.player.state.value
            appendLine(
                "- Now: ${if (c.online.canGoOnline) "online" else "offline"}, " +
                    (playing.current?.let { "playing from ${it.source.label}${if (playing.isPlaying) "" else " (paused)"}" } ?: "nothing playing"),
            )
        }
        if (screens.isNotEmpty()) appendLine("- Screens, last one open: ${screens.joinToString(" → ")}")
    }
}
