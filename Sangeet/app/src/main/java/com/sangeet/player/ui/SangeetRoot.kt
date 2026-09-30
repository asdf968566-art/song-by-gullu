package com.sangeet.player.ui

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
import com.sangeet.player.ui.settings.SourcesScreen
import com.sangeet.player.ui.settings.ThemePickerScreen
import com.sangeet.player.ui.theme.Sangeet
import com.sangeet.player.ui.theme.ThemedBackground

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val THEMES = "settings/themes"
    const val SOURCES = "settings/sources"
    const val EQUALIZER = "settings/equalizer"
    const val IMPORT = "import"
    const val PLAYLIST = "playlist/{id}"
    const val LIST = "list/{kind}?arg={arg}"

    fun playlist(id: Long) = "playlist/$id"
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

    // Permission pehle se mili ho to phone ke gaane scan karo.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            container.local.scan()
        }
    }

    BackHandler(enabled = expanded) { expanded = false }

    ThemedBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (state.current != null) MiniPlayer(onExpand = { expanded = true })
                    BottomNav(nav)
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.THEMES) { ThemePickerScreen(nav) }
                composable(Routes.SOURCES) { SourcesScreen(nav) }
                composable(Routes.EQUALIZER) { EqualizerScreen(nav) }
                composable(Routes.IMPORT) { ImportPlaylistScreen(nav) }
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

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun BottomNav(nav: NavHostController) {
    val spec = Sangeet.spec
    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Tab(Routes.SEARCH, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
        Tab(Routes.LIBRARY, "Your Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    NavigationBar(containerColor = spec.navBar, tonalElevation = 0.dp) {
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
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
