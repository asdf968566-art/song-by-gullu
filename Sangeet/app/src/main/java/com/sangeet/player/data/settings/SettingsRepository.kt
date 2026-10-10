package com.sangeet.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.model.AudioQuality
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeStyle(val label: String, val tagline: String) {
    SPOTIFY("Classic Dark", "Sleek dark look"),
    AURORA("Aurora", "Animated northern-lights gradient"),
    GLASS("Glassmorphism", "Frosted glass cards"),
    NEUMORPHISM("Neumorphism", "Soft 3D raised buttons"),
    AMOLED("AMOLED Black", "Pure black, saves battery"),
    MATERIAL_YOU("Material You", "Colors from your wallpaper (Android 12+)"),
    LIQUID_GLASS("Liquid Glass", "iOS 26 style: floating glass bars over the content"),
}

enum class DarkMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class AccentColor(val label: String, val argb: Long) {
    GREEN("Green", 0xFF1DB954),
    VIOLET("Violet", 0xFF8B5CF6),
    BLUE("Blue", 0xFF3B82F6),
    TEAL("Teal", 0xFF14B8A6),
    ORANGE("Orange", 0xFFF97316),
    PINK("Pink", 0xFFEC4899),
    RED("Red", 0xFFEF4444),
}

data class AppSettings(
    val theme: ThemeStyle = ThemeStyle.SPOTIFY,
    val darkMode: DarkMode = DarkMode.DARK,
    val accent: AccentColor = AccentColor.GREEN,
    val wifiQuality: AudioQuality = AudioQuality.HIGH,
    val mobileQuality: AudioQuality = AudioQuality.MEDIUM,
    val downloadQuality: AudioQuality = AudioQuality.HIGH,
    val offlineMode: Boolean = false,
    val downloadOnWifiOnly: Boolean = false,
    /** Also keep a copy of each download in the phone's Music/Sangeet folder (stays after uninstall). */
    val saveToPhone: Boolean = false,
    val autoLyrics: Boolean = true,
    val skipSilence: Boolean = false,
    val playbackSpeed: Float = 1f,
    val autoplay: Boolean = true,
    /** Gaano ke beech fade (seconds). 0 = band. */
    val crossfadeSec: Int = 4,
    /** For You feed mein gaana hook (chorus ke paas) se shuru ho. */
    val hookPreview: Boolean = false,
    /** Headphone / Bluetooth wapas lagate hi gaana resume. */
    val headphoneResume: Boolean = false,
    /** Wi-Fi + charging pe liked aur Daily Mix apne aap download. */
    val smartDownloads: Boolean = true,
    val autoPlaylists: Boolean = true,
    /** Send anonymous listening data once a day, so suggestions learn from all listeners (Community). */
    val shareListening: Boolean = true,
    /** Download and install new versions by itself (AutoUpdate). */
    val autoUpdate: Boolean = true,
    /** A notification when a singer you play most has a new song (NewSongs). */
    val newSongAlerts: Boolean = true,
    /** One song picked for you every morning (SongOfTheDay). */
    val songOfTheDay: Boolean = true,
    val audiusEnabled: Boolean = true,
    val jiosaavnEnabled: Boolean = true,
    val youtubeEnabled: Boolean = true,
    /** Optional: YouTube Data API v3 key (metadata/search/trending ke liye). Khaali = bina key (NewPipe). */
    val youtubeApiKey: String = BuildConfig.YOUTUBE_API_KEY,
    /** Pasandida bhashayein, jaise hindi, punjabi. Trending aur suggestions inhi se. */
    val languages: List<String> = listOf("hindi", "punjabi"),
    /** App update ke liye read-only GitHub token (private repo). */
    val githubToken: String = "",
    /** Optional Anthropic API key for the AI DJ (Claude). Empty = built-in DJ. */
    val anthropicApiKey: String = "",
    /** Without a key, the AI DJ also asks a free online AI for song ideas (FreeAi). */
    val freeAi: Boolean = true,
    /** Optional own Google Gemini key (free at aistudio.google.com); empty = the app's built-in one, if any. */
    val geminiApiKey: String = "",
    val jamendoClientId: String = "",
    val subsonicUrl: String = "",
    val subsonicUser: String = "",
    val subsonicToken: String = "",
    val subsonicSalt: String = "",
) {
    val subsonicConfigured: Boolean
        get() = subsonicUrl.isNotBlank() && subsonicUser.isNotBlank() && subsonicToken.isNotBlank()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val darkMode = stringPreferencesKey("dark_mode")
        val accent = stringPreferencesKey("accent")
        val wifiQuality = stringPreferencesKey("wifi_quality")
        val mobileQuality = stringPreferencesKey("mobile_quality")
        val downloadQuality = stringPreferencesKey("download_quality")
        val offlineMode = booleanPreferencesKey("offline_mode")
        val downloadOnWifiOnly = booleanPreferencesKey("download_wifi_only")
        val saveToPhone = booleanPreferencesKey("save_to_phone")
        val autoLyrics = booleanPreferencesKey("auto_lyrics")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val autoplay = booleanPreferencesKey("autoplay")
        val crossfadeSec = intPreferencesKey("crossfade_sec")
        val hookPreview = booleanPreferencesKey("hook_preview")
        val headphoneResume = booleanPreferencesKey("headphone_resume")
        val smartDownloads = booleanPreferencesKey("smart_downloads")
        val autoPlaylists = booleanPreferencesKey("auto_playlists")
        val shareListening = booleanPreferencesKey("share_listening")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val newSongAlerts = booleanPreferencesKey("new_song_alerts")
        val songOfTheDay = booleanPreferencesKey("song_of_the_day")
        val audiusEnabled = booleanPreferencesKey("audius_enabled")
        val jiosaavnEnabled = booleanPreferencesKey("jiosaavn_enabled")
        val youtubeEnabled = booleanPreferencesKey("youtube_enabled")
        val youtubeApiKey = stringPreferencesKey("youtube_api_key")
        val languages = stringPreferencesKey("languages")
        val githubToken = stringPreferencesKey("github_token")
        val anthropicApiKey = stringPreferencesKey("anthropic_api_key")
        val freeAi = booleanPreferencesKey("free_ai")
        val geminiApiKey = stringPreferencesKey("gemini_api_key")
        val jamendoClientId = stringPreferencesKey("jamendo_client_id")
        val subsonicUrl = stringPreferencesKey("subsonic_url")
        val subsonicUser = stringPreferencesKey("subsonic_user")
        val subsonicToken = stringPreferencesKey("subsonic_token")
        val subsonicSalt = stringPreferencesKey("subsonic_salt")
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    val settings: StateFlow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p.enumOf(Keys.theme, d.theme),
            darkMode = p.enumOf(Keys.darkMode, d.darkMode),
            accent = p.enumOf(Keys.accent, d.accent),
            wifiQuality = p.enumOf(Keys.wifiQuality, d.wifiQuality),
            mobileQuality = p.enumOf(Keys.mobileQuality, d.mobileQuality),
            downloadQuality = p.enumOf(Keys.downloadQuality, d.downloadQuality),
            offlineMode = p[Keys.offlineMode] ?: d.offlineMode,
            downloadOnWifiOnly = p[Keys.downloadOnWifiOnly] ?: d.downloadOnWifiOnly,
            saveToPhone = p[Keys.saveToPhone] ?: d.saveToPhone,
            autoLyrics = p[Keys.autoLyrics] ?: d.autoLyrics,
            skipSilence = p[Keys.skipSilence] ?: d.skipSilence,
            playbackSpeed = p[Keys.playbackSpeed] ?: d.playbackSpeed,
            autoplay = p[Keys.autoplay] ?: d.autoplay,
            crossfadeSec = p[Keys.crossfadeSec] ?: d.crossfadeSec,
            hookPreview = p[Keys.hookPreview] ?: d.hookPreview,
            headphoneResume = p[Keys.headphoneResume] ?: d.headphoneResume,
            smartDownloads = p[Keys.smartDownloads] ?: d.smartDownloads,
            autoPlaylists = p[Keys.autoPlaylists] ?: d.autoPlaylists,
            shareListening = p[Keys.shareListening] ?: d.shareListening,
            autoUpdate = p[Keys.autoUpdate] ?: d.autoUpdate,
            newSongAlerts = p[Keys.newSongAlerts] ?: d.newSongAlerts,
            songOfTheDay = p[Keys.songOfTheDay] ?: d.songOfTheDay,
            audiusEnabled = p[Keys.audiusEnabled] ?: d.audiusEnabled,
            jiosaavnEnabled = p[Keys.jiosaavnEnabled] ?: d.jiosaavnEnabled,
            youtubeEnabled = p[Keys.youtubeEnabled] ?: d.youtubeEnabled,
            youtubeApiKey = p[Keys.youtubeApiKey] ?: d.youtubeApiKey,
            languages = p[Keys.languages]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() } ?: d.languages,
            githubToken = p[Keys.githubToken] ?: d.githubToken,
            anthropicApiKey = p[Keys.anthropicApiKey] ?: d.anthropicApiKey,
            freeAi = p[Keys.freeAi] ?: d.freeAi,
            geminiApiKey = p[Keys.geminiApiKey] ?: d.geminiApiKey,
            jamendoClientId = p[Keys.jamendoClientId] ?: d.jamendoClientId,
            subsonicUrl = p[Keys.subsonicUrl] ?: d.subsonicUrl,
            subsonicUser = p[Keys.subsonicUser] ?: d.subsonicUser,
            subsonicToken = p[Keys.subsonicToken] ?: d.subsonicToken,
            subsonicSalt = p[Keys.subsonicSalt] ?: d.subsonicSalt,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Resolver jaise blocking code ke liye turant value. */
    val current: AppSettings get() = settings.value

    suspend fun setTheme(v: ThemeStyle) = context.dataStore.edit { it[Keys.theme] = v.name }
    suspend fun setDarkMode(v: DarkMode) = context.dataStore.edit { it[Keys.darkMode] = v.name }
    suspend fun setAccent(v: AccentColor) = context.dataStore.edit { it[Keys.accent] = v.name }
    suspend fun setWifiQuality(v: AudioQuality) = context.dataStore.edit { it[Keys.wifiQuality] = v.name }
    suspend fun setMobileQuality(v: AudioQuality) = context.dataStore.edit { it[Keys.mobileQuality] = v.name }
    suspend fun setDownloadQuality(v: AudioQuality) = context.dataStore.edit { it[Keys.downloadQuality] = v.name }
    suspend fun setOfflineMode(v: Boolean) = context.dataStore.edit { it[Keys.offlineMode] = v }
    suspend fun setDownloadOnWifiOnly(v: Boolean) = context.dataStore.edit { it[Keys.downloadOnWifiOnly] = v }
    suspend fun setSaveToPhone(v: Boolean) = context.dataStore.edit { it[Keys.saveToPhone] = v }
    suspend fun setAutoLyrics(v: Boolean) = context.dataStore.edit { it[Keys.autoLyrics] = v }
    suspend fun setSkipSilence(v: Boolean) = context.dataStore.edit { it[Keys.skipSilence] = v }
    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[Keys.playbackSpeed] = v }
    suspend fun setCrossfadeSec(v: Int) = context.dataStore.edit { it[Keys.crossfadeSec] = v }
    suspend fun setHookPreview(v: Boolean) = context.dataStore.edit { it[Keys.hookPreview] = v }
    suspend fun setHeadphoneResume(v: Boolean) = context.dataStore.edit { it[Keys.headphoneResume] = v }
    suspend fun setSmartDownloads(v: Boolean) = context.dataStore.edit { it[Keys.smartDownloads] = v }
    suspend fun setAutoplay(v: Boolean) = context.dataStore.edit { it[Keys.autoplay] = v }
    suspend fun setAutoPlaylists(v: Boolean) = context.dataStore.edit { it[Keys.autoPlaylists] = v }
    suspend fun setShareListening(v: Boolean) = context.dataStore.edit { it[Keys.shareListening] = v }
    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[Keys.autoUpdate] = v }
    suspend fun setNewSongAlerts(v: Boolean) = context.dataStore.edit { it[Keys.newSongAlerts] = v }
    suspend fun setSongOfTheDay(v: Boolean) = context.dataStore.edit { it[Keys.songOfTheDay] = v }
    suspend fun setAudiusEnabled(v: Boolean) = context.dataStore.edit { it[Keys.audiusEnabled] = v }
    suspend fun setJioSaavnEnabled(v: Boolean) = context.dataStore.edit { it[Keys.jiosaavnEnabled] = v }
    suspend fun setYouTubeEnabled(v: Boolean) = context.dataStore.edit { it[Keys.youtubeEnabled] = v }
    suspend fun setYouTubeApiKey(v: String) = context.dataStore.edit { it[Keys.youtubeApiKey] = v.trim() }
    suspend fun setLanguages(v: List<String>) = context.dataStore.edit { it[Keys.languages] = v.joinToString(",") }
    suspend fun setAnthropicApiKey(v: String) = context.dataStore.edit { it[Keys.anthropicApiKey] = v.trim() }
    suspend fun setFreeAi(v: Boolean) = context.dataStore.edit { it[Keys.freeAi] = v }
    suspend fun setGeminiApiKey(v: String) = context.dataStore.edit { it[Keys.geminiApiKey] = v.trim() }
    suspend fun setGithubToken(v: String) = context.dataStore.edit { it[Keys.githubToken] = v.trim() }
    suspend fun setJamendoClientId(v: String) = context.dataStore.edit { it[Keys.jamendoClientId] = v.trim() }

    /** Password save nahi hota, sirf Subsonic token = md5(password + salt). */
    suspend fun setSubsonic(url: String, user: String, password: String) = context.dataStore.edit {
        it[Keys.subsonicUrl] = url.trim().trimEnd('/')
        it[Keys.subsonicUser] = user.trim()
        if (password.isNotEmpty()) {
            val salt = UUID.randomUUID().toString().replace("-", "").take(12)
            it[Keys.subsonicSalt] = salt
            it[Keys.subsonicToken] = md5(password + salt)
        }
    }

    suspend fun clearSubsonic() = context.dataStore.edit {
        it.remove(Keys.subsonicUrl); it.remove(Keys.subsonicUser)
        it.remove(Keys.subsonicToken); it.remove(Keys.subsonicSalt)
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }
}
