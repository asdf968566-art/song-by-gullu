package com.sangeet.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sangeet.player.data.LibraryRepository
import com.sangeet.player.data.LocalMusicRepository
import com.sangeet.player.data.NetworkMonitor
import com.sangeet.player.data.OnlineRepository
import com.sangeet.player.data.db.SangeetDatabase
import com.sangeet.player.data.download.DownloadRepository
import com.sangeet.player.data.lyrics.LyricsRepository
import com.sangeet.player.data.playlist.PlaylistImporter
import com.sangeet.player.data.recommend.RecommendationRepository
import com.sangeet.player.data.remote.Http
import com.sangeet.player.data.settings.SettingsRepository
import com.sangeet.player.playback.EqualizerManager
import com.sangeet.player.playback.PlayerConnection
import com.sangeet.player.playback.StreamResolver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SangeetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Saari repositories ek jagah (simple manual dependency injection). */
@OptIn(UnstableApi::class)
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database = SangeetDatabase.create(app)
    val settings = SettingsRepository(app, scope)
    val network = NetworkMonitor(app)
    val online = OnlineRepository(settings, network)
    val local = LocalMusicRepository(app)
    val library = LibraryRepository(database, scope)
    val downloads = DownloadRepository(app, database.downloadDao(), library, settings, scope)
    val lyrics = LyricsRepository(database.lyricsDao(), online)
    val importer = PlaylistImporter(app, local, online)
    val equalizer = EqualizerManager(app)
    val recommendations = RecommendationRepository(app, library, local, online, settings)
    val player = PlayerConnection(app, library, scope).apply {
        radio = { seed, exclude -> recommendations.radio(seed, exclude) }
        autoplayEnabled = { settings.current.autoplay }
    }

    init {
        // Track record se auto playlists roz update hoti rehti hain.
        scope.launch {
            delay(8_000)
            if (settings.current.autoPlaylists) runCatching { recommendations.syncAutoPlaylists() }
        }
    }

    /** Stream kiye gaane 512 MB tak cache mein rehte hain, dobara bajane pe data nahi lagta. */
    private val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            StandaloneDatabaseProvider(app),
        )
    }

    val dataSourceFactory: DataSource.Factory by lazy {
        val http = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.USER_AGENT)
        val cached = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // DefaultDataSource file:// aur content:// khud padhta hai, http ko cache se bhejta hai.
        val routed = DefaultDataSource.Factory(app, cached)
        ResolvingDataSource.Factory(routed, StreamResolver(library, online, downloads))
    }
}
