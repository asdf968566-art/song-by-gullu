package com.sangeet.player.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sangeet.player.ui.components.MiniPlayer
import com.sangeet.player.ui.components.UpdateDialog
import com.sangeet.player.ui.discover.DiscoverScreen
import com.sangeet.player.ui.library.MixScreen
import com.sangeet.player.ui.library.MovieScreen
import com.sangeet.player.ui.library.MoviesScreen
import com.sangeet.player.ui.library.ArtistScreen
import com.sangeet.player.ui.library.StatsScreen
import com.sangeet.player.ui.dj.AiDjScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.sangeet.player.ui.library.OnlineLibraryScreen
import com.sangeet.player.ui.library.OnlinePlaylistScreen
import com.sangeet.player.ui.home.HomeScreen
import com.sangeet.player.ui.library.ImportPlaylistScreen
import com.sangeet.player.ui.library.LibraryScreen
import com.sangeet.player.ui.library.ListKind
import com.sangeet.player.ui.library.PlaylistScreen
import com.sangeet.player.ui.library.TrackListScreen
import com.sangeet.player.ui.nowplaying.NowPlayingScreen
import com.sangeet.player.ui.search.SearchScreen
import com.sangeet.player.ui.settings.EqualizerScreen
import com.sangeet.player.ui.settings.SettingsScreen
import com.sangeet.player.ui.settings.ThemePickerScreen
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.ThemedBackground
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.theme.liquidGlass
import com.sangeet.player.ui.theme.rememberGlassSource
import com.sangeet.player.ui.theme.glassSource
import com.sangeet.player.ui.theme.LocalGlassSource
import com.sangeet.player.ui.theme.LocalBottomBarSpace
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val MIX = "mix/{id}"
    const val ARTIST = "artist/{name}"
    const val STATS = "stats"
    const val DJ = "dj"
    const val ONLINE_LIBRARY = "online"
    const val ONLINE_PLAYLIST = "online/{id}?title={title}"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val THEMES = "settings/themes"
    const val SOURCES = "settings/sources"
    const val EQUALIZER = "settings/equalizer"
    const val IMPORT = "import"
    const val DOWNLOADS = "downloads"
    const val REPORT = "report"
    const val OWNER = "settings/owner"
    const val ALARM = "settings/alarm"
    const val SYNC = "settings/sync?code={code}"
    fun sync(code: String? = null) = "settings/sync" + (code?.let { "?code=" + android.net.Uri.encode(it) } ?: "")
    const val PLAYLIST = "playlist/{id}"
    const val LIST = "list/{kind}?arg={arg}"
    const val MOVIES = "movies?q={q}"
    const val MOVIE = "movie?title={title}&year={year}&album={album}"

    fun playlist(id: Long) = "playlist/$id"
    fun mix(id: String) = "mix/$id"
    fun artist(name: String) = "artist/${android.net.Uri.encode(name.substringBefore(",").trim())}"
    fun onlinePlaylist(id: String, title: String) = "online/$id?title=${android.net.Uri.encode(title)}"
    fun list(kind: ListKind, arg: String = "") = "list/${kind.name}?arg=${android.net.Uri.encode(arg)}"
    fun movies(q: String = "") = "movies?q=${android.net.Uri.encode(q)}"
    fun movie(title: String, year: Int, albumId: String = "") =
        "movie?title=${android.net.Uri.encode(title)}&year=$year&album=${android.net.Uri.encode(albumId)}"
}

val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun SangeetRoot() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val nav = rememberNavController()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state by container.player.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val onDiscover = backStack?.destination?.route == Routes.DISCOVER
    // Which screens are used (owner dashboard, through the Community upload).
    val openedRoute = backStack?.destination?.route
    LaunchedEffect(openedRoute) { com.sangeet.player.data.Usage.opened(context, openedRoute) }

    // Permission pehle se mili ho to phone ke gaane scan karo.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            container.local.scan()
        }
    }

    // Android 13+: ask once to show notifications (downloads, updates, new songs from your singers).
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val prefs = context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notify", false) &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                prefs.edit().putBoolean("asked_notify", true).apply()
                runCatching { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
    // Naya version chupchaap check karo (6 ghante mein ek baar)
    LaunchedEffect(Unit) { runCatching { container.updater.checkIfDue() } }
    // Opened from a notification (e.g. the download progress): go to that screen.
    LaunchedEffect(Unit) {
        container.openRoute.collect { route -> if (route != null) { nav.navigate(route); container.openRoute.value = null } }
    }
    UpdateDialog()
    // After an update: what's new in it (once).
    val appContext = LocalContext.current
    var whatsNew by remember { mutableStateOf(com.sangeet.player.data.WhatsNew.unseen(appContext)) }
    if (whatsNew.isNotEmpty()) {
        com.sangeet.player.ui.components.WhatsNewDialog(whatsNew) {
            com.sangeet.player.data.WhatsNew.markSeen(appContext)
            whatsNew = emptyList()
        }
    }

    BackHandler(enabled = expanded) { expanded = false }

    // Status bar (time, battery, network) and navigation bar icons follow the app's theme, not the phone's:
    // light icons on dark pages, dark icons on light ones. For You is always dark.
    val view = LocalView.current
    val darkTop = Sangeet.spec.isDark || (onDiscover && !expanded)
    LaunchedEffect(darkTop) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        (ctx as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !darkTop
                isAppearanceLightNavigationBars = !darkTop
            }
        }
    }

    // Liquid Glass: the pages run under the floating glass bars, which show them through the glass.
    val liquid = Sangeet.spec.style == ThemeStyle.LIQUID_GLASS
    val glass = rememberGlassSource()
    CompositionLocalProvider(LocalNav provides nav, LocalGlassSource provides glass.takeIf { liquid }) {
    ThemedBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    // Discover feed ka apna bada player hai, wahan mini player nahi.
                    if (state.current != null && !onDiscover) MiniPlayer(onExpand = { expanded = true })
                    BottomNav(nav)
                }
            },
        ) { padding ->
            CompositionLocalProvider(LocalBottomBarSpace provides if (liquid) padding.calculateBottomPadding() else 0.dp) {
            NavHost(
                nav,
                startDestination = Routes.DISCOVER,
                modifier = if (liquid) Modifier.padding(top = padding.calculateTopPadding()).glassSource(glass) else Modifier.padding(padding),
                // Quick fades: the default 700 ms cross-fade felt slow and left two screens on top of each other.
                enterTransition = { fadeIn(tween(140)) },
                exitTransition = { fadeOut(tween(90)) },
                popEnterTransition = { fadeIn(tween(140)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.DISCOVER) { DiscoverScreen(nav) }
                composable(Routes.ONLINE_LIBRARY) { OnlineLibraryScreen(nav) }
                composable(Routes.STATS) { StatsScreen(nav) }
                composable(Routes.DJ) { AiDjScreen(nav) }
                composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                    ArtistScreen(nav, entry.arguments?.getString("name") ?: "")
                }
                composable(
                    Routes.ONLINE_PLAYLIST,
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "Playlist" },
                    ),
                ) { entry ->
                    OnlinePlaylistScreen(nav, entry.arguments?.getString("id") ?: "", entry.arguments?.getString("title") ?: "Playlist")
                }
                composable(Routes.MOVIES, arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { entry ->
                    MoviesScreen(nav, entry.arguments?.getString("q") ?: "")
                }
                composable(
                    Routes.MOVIE,
                    arguments = listOf(
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("year") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("album") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    MovieScreen(nav, entry.arguments?.getString("title") ?: "", entry.arguments?.getInt("year") ?: 0, entry.arguments?.getString("album") ?: "")
                }
                composable(Routes.MIX, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    MixScreen(nav, entry.arguments?.getString("id") ?: "")
                }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.THEMES) { ThemePickerScreen(nav) }
                composable(Routes.SOURCES) { com.sangeet.player.ui.settings.LockedSourcesScreen(nav) }
                composable(Routes.REPORT) { com.sangeet.player.ui.settings.ReportScreen(nav) }
                composable(Routes.OWNER) { com.sangeet.player.ui.settings.OwnerDashboard(nav) }
                composable(Routes.ALARM) { com.sangeet.player.ui.settings.AlarmScreen(nav) }
                composable(
                    Routes.SYNC,
                    arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { e -> com.sangeet.player.ui.settings.SyncScreen(nav, e.arguments?.getString("code")) }
                composable(Routes.EQUALIZER) { EqualizerScreen(nav) }
                composable(Routes.IMPORT) { ImportPlaylistScreen(nav) }
                composable(Routes.DOWNLOADS) { com.sangeet.player.ui.library.DownloadsScreen(nav) }
                composable(
                    Routes.PLAYLIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    PlaylistScreen(nav, entry.arguments?.getLong("id") ?: 0L)
                }
                composable(
                    Routes.LIST,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType },
                        navArgument("arg") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val kind = runCatching { ListKind.valueOf(entry.arguments?.getString("kind") ?: "") }
                        .getOrDefault(ListKind.LIKED)
                    TrackListScreen(nav, kind, entry.arguments?.getString("arg") ?: "")
                }
            }
            }
        }

        AnimatedVisibility(
            visible = expanded && state.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(onCollapse = { expanded = false })
        }
    }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun BottomNav(nav: NavHostController) {
    val spec = Sangeet.spec
    val tabs = listOf(
        Tab(Routes.DISCOVER, "For You", Icons.Outlined.Explore, Icons.Rounded.Explore),
        Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Tab(Routes.SEARCH, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
        Tab(Routes.LIBRARY, "Your Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val go: (String) -> Unit = { r ->
        // Already inside this tab (e.g. Settings opened from Home): back to the tab's own screen.
        // Otherwise switch tabs, keeping where each tab was.
        if (!nav.popBackStack(r, inclusive = false)) nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (spec.style == ThemeStyle.LIQUID_GLASS) {
        LiquidTabBar(tabs, route, go)
        return
    }
    NavigationBar(containerColor = spec.navBar, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { go(tab.route) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = spec.onSurface,
                    selectedTextColor = spec.onSurface,
                    unselectedIconColor = spec.muted,
                    unselectedTextColor = spec.muted,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

/**
 * iOS 26 tab bar: a floating glass capsule with the tabs (the chosen one sits in a lighter glass pill),
 * and Search on its own round glass button at the right.
 */
@Composable
private fun LiquidTabBar(tabs: List<Tab>, route: String?, go: (String) -> Unit) {
    val spec = Sangeet.spec
    val search = tabs.first { it.route == Routes.SEARCH }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .liquidGlass(spec, RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            tabs.filter { it != search }.forEach { tab ->
                val selected = route == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) (if (spec.isDark) Color.White.copy(alpha = 0.14f) else spec.accent.copy(alpha = 0.16f)) else Color.Transparent)
                        .clickable { go(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) spec.accent else spec.onSurface
                    Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val onSearch = route == Routes.SEARCH
        Box(
            Modifier
                .size(62.dp)
                .liquidGlass(spec, CircleShape)
                .clickable { go(Routes.SEARCH) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (onSearch) search.selectedIcon else search.icon, search.label, tint = if (onSearch) spec.accent else spec.onSurface, modifier = Modifier.size(26.dp))
        }
    }
}
