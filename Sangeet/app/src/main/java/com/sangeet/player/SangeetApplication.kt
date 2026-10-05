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
import com.sangeet.player.data.download.SmartDownloadWorker
import com.sangeet.player.data.db.ListenEntity
import com.sangeet.player.data.lyrics.LyricsRepository
import com.sangeet.player.data.playlist.PlaylistImporter
import com.sangeet.player.data.recommend.RecommendationRepository
import com.sangeet.player.data.remote.Http
import com.sangeet.player.data.settings.SettingsRepository
import com.sangeet.player.data.update.AppUpdater
import com.sangeet.player.playback.EqualizerManager
import com.sangeet.player.playback.PlayerConnection
import com.sangeet.player.playback.StreamResolver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.sangeet.player.data.model.SourceType

class SangeetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.sangeet.player.data.CrashReporter.install(this)
        container = AppContainer(this)
    }
}

/** Saari repositories ek jagah (simple manual dependency injection). */
@OptIn(UnstableApi::class)
class AppContainer(private val app: Application) {
    val appContext: android.content.Context get() = app
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** A screen to open when the app is launched from a notification. */
    val openRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

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
    val updater = AppUpdater(app, settings)
    val aiDj = com.sangeet.player.data.ai.AiDj(online, settings)
    val recommendations = RecommendationRepository(app, library, local, online, settings, com.sangeet.player.data.recommend.CatalogPool(app, scope))
    val player = PlayerConnection(app, library, scope).apply {
        radio = { seed, exclude -> recommendations.radio(seed, exclude) }
        autoplayEnabled = { settings.current.autoplay }
        crossfadeMs = { settings.current.crossfadeSec * 1000L }
        onListened = { t, start, ms ->
            database.listenDao().insert(ListenEntity(trackId = t.id, title = t.title, artist = t.artist, startedAt = start, playedMs = ms))
        }
    }

    init {
        // Home screen widget ko player ke saath update rakho
        scope.launch {
            player.state.map { Triple(it.current?.id, it.isPlaying, it.current?.artworkUrl) }.distinctUntilChanged().collect {
                runCatching { com.sangeet.player.ui.widget.SangeetWidget.update(app, player.state.value) }
            }
        }
        // Smart downloads on/off ke hisaab se roz ka kaam lagao / hatao
        scope.launch {
            settings.settings.map { it.smartDownloads }.distinctUntilChanged().collect {
                runCatching { SmartDownloadWorker.schedule(app, it) }
            }
        }
        // Agle 2 gaanon ka YouTube link pehle se nikaal lo, taaki next dabate hi bajne lage.
        scope.launch {
            player.state.map { it.queueIndex to it.queue.map { t -> t.id } }.distinctUntilChanged().collect { (idx, _) ->
                val q = player.state.value.queue
                val upcoming = (1..2).mapNotNull { q.getOrNull(idx + it) }.filter { it.source == SourceType.YOUTUBE }
                if (upcoming.isEmpty() || !online.canGoOnline) return@collect
                launch(Dispatchers.IO) {
                    upcoming.forEach { t -> runCatching { online.streamUrl(t, online.streamingQuality()) } }
                }
            }
        }
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
