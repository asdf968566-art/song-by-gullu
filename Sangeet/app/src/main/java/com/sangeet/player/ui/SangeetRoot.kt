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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarDefaults
import com.sangeet.player.data.settings.ThemeStyle
import com.sangeet.player.ui.theme.liquidGlass

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
    const val PLAYLIST = "playlist/{id}"
    const val LIST = "list/{kind}?arg={arg}"

    fun playlist(id: Long) = "playlist/$id"
    fun mix(id: String) = "mix/$id"
    fun artist(name: String) = "artist/${android.net.Uri.encode(name.substringBefore(",").trim())}"
    fun onlinePlaylist(id: String, title: String) = "online/$id?title=${android.net.Uri.encode(title)}"
    fun list(kind: ListKind, arg: String = "") = "list/${kind.name}?arg=${android.net.Uri.encode(arg)}"
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

    // Permission pehle se mili ho to phone ke gaane scan karo.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            container.local.scan()
        }
    }

    // Naya version chupchaap check karo (6 ghante mein ek baar)
    LaunchedEffect(Unit) { runCatching { container.updater.checkIfDue() } }
    // Opened from a notification (e.g. the download progress): go to that screen.
    LaunchedEffect(Unit) {
        container.openRoute.collect { route -> if (route != null) { nav.navigate(route); container.openRoute.value = null } }
    }
    UpdateDialog()

    BackHandler(enabled = expanded) { expanded = false }

    CompositionLocalProvider(LocalNav provides nav) {
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
            NavHost(
                nav,
                startDestination = Routes.DISCOVER,
                modifier = Modifier.padding(padding),
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
                composable(Routes.MIX, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    MixScreen(nav, entry.arguments?.getString("id") ?: "")
                }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.THEMES) { ThemePickerScreen(nav) }
                composable(Routes.SOURCES) { com.sangeet.player.ui.settings.LockedSourcesScreen(nav) }
                composable(Routes.REPORT) { com.sangeet.player.ui.settings.ReportScreen(nav) }
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
    val liquid = spec.style == ThemeStyle.LIQUID_GLASS
    // Liquid Glass: the tab bar floats as a glass capsule above the bottom edge.
    NavigationBar(
        containerColor = if (liquid) Color.Transparent else spec.navBar,
        tonalElevation = 0.dp,
        windowInsets = if (liquid) WindowInsets(0) else NavigationBarDefaults.windowInsets,
        modifier = if (liquid) Modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
            .liquidGlass(spec, RoundedCornerShape(30.dp)) else Modifier,
    ) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = spec.onSurface,
                    selectedTextColor = spec.onSurface,
                    unselectedIconColor = spec.muted,
                    unselectedTextColor = spec.muted,
                    indicatorColor = if (liquid) Color.White.copy(alpha = if (spec.isDark) 0.16f else 0.55f) else Color.Transparent,
                ),
            )
        }
    }
}
