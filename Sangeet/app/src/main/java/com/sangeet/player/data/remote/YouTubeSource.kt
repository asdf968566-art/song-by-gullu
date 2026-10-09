package com.sangeet.player.data.remote

import com.sangeet.player.data.model.AudioQuality
import com.sangeet.player.data.model.SourceType
import com.sangeet.player.data.model.Track
import com.sangeet.player.data.settings.AppSettings
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube / YouTube Music.
 *  - Gaana bajana: NewPipeExtractor (yt-dlp jaisa, bina key).
 *  - Search / trending: YouTube Data API v3 agar key di ho, warna NewPipe (YouTube Music songs).
 * Sirf personal use ke liye.
 */
class YouTubeSource : OnlineSource {
    override val type = SourceType.YOUTUBE
    override val exactQuality = false

    override fun isEnabled(s: AppSettings) = s.youtubeEnabled

    override suspend fun search(query: String, s: AppSettings): List<Track> {
        if (s.youtubeApiKey.isNotBlank()) {
            runCatching { return DataApi.search(query, s.youtubeApiKey) }
        }
        return musicSearch(query)
    }

    override suspend fun trending(s: AppSettings, genre: String?): List<Track> {
        if (genre != null) return byLanguage(genre, s)
        if (s.youtubeApiKey.isNotBlank()) {
            runCatching { DataApi.mostPopularMusic(s.youtubeApiKey) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return musicSearch("${s.languages.firstOrNull() ?: "hindi"} top songs this week")
    }

    override suspend fun byLanguage(language: String, s: AppSettings): List<Track> {
        val q = if (language.equals("pahadi", true)) "latest garhwali kumaoni pahadi songs" else "latest $language songs"
        return search(q, s).map { if (it.language.isBlank()) it.copy(language = language.lowercase()) else it }
    }

    /** Player ke loader thread pe chalta hai (blocking theek hai). Link ~ghante bhar cache rehta hai. */
    override fun streamUrl(track: Track, quality: AudioQuality, s: AppSettings): String = try {
        resolveStream(track, quality)
    } catch (e: Exception) {
        android.util.Log.w("Sangeet", "YouTube stream fail ${track.sourceId}: ${e.javaClass.simpleName}: ${e.message}", e)
        throw IOException("YouTube: ${e.message ?: e.javaClass.simpleName}", e)
    }

    private fun resolveStream(track: Track, quality: AudioQuality): String {
        val key = "${track.sourceId}@${quality.name}"
        streamCache[key]?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first }
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl(track.sourceId))
        val streams = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (streams.isEmpty()) throw IOException("No YouTube audio found")
        val target = if (quality == AudioQuality.LOW) 64 else quality.kbps
        val pick = streams.filter { it.averageBitrate in 1..target }.maxByOrNull { it.averageBitrate }
            ?: streams.minByOrNull { if (it.averageBitrate > 0) it.averageBitrate else Int.MAX_VALUE }!!
        val url = pick.content
        streamCache[key] = url to System.currentTimeMillis() + 60 * 60_000L
        android.util.Log.i("Sangeet", "YouTube stream ${track.sourceId}: ${pick.format} ${pick.averageBitrate}kbps")
        return url
    }

    /**
     * YouTube Mix / YT Music radio for a song: what YouTube's listeners play next.
     * Great source of variety for the feed and radio.
     */
    suspend fun similar(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        ensureInit()
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId&list=RD$videoId")
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { toTrack(it) }.filter { it.sourceId != videoId }
    }

    /**
     * A public YouTube / YouTube Music playlist link -> (name, songs), up to ~2000 songs. With an API key the
     * official API reads it (1 quota unit per 50 songs, never blocked); otherwise, or if that fails, NewPipe.
     */
    suspend fun playlist(link: String, key: String = ""): Pair<String, List<Track>>? = withContext(Dispatchers.IO) {
        val id = Regex("[?&]list=([\\w-]+)").find(link)?.groupValues?.get(1) ?: return@withContext null
        if (key.isNotBlank()) {
            runCatching { DataApi.playlist(id, key) }
                .onFailure { android.util.Log.w("Sangeet", "YouTube API playlist $id: ${it.message}") }
                .getOrNull()?.takeIf { it.second.isNotEmpty() }?.let { return@withContext it }
        }
        runCatching { newPipePlaylist(id) }
            .onFailure { android.util.Log.w("Sangeet", "NewPipe playlist $id: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()?.takeIf { it.second.isNotEmpty() }
    }

    private fun newPipePlaylist(id: String): Pair<String, List<Track>> {
        ensureInit()
        val url = "https://www.youtube.com/playlist?list=$id"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val out = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { toTrack(it) }.toMutableList()
        var page = info.nextPage
        var n = 0
        while (page != null && n < 20) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            out += more.items.filterIsInstance<StreamInfoItem>().mapNotNull { toTrack(it) }
            page = more.nextPage
            n++
        }
        return (info.name ?: "YouTube playlist") to out.distinctBy { it.id }
    }

    /** First YouTube Music result for "title artist" (to start a YouTube radio from any song). */
    suspend fun find(title: String, artist: String): Track? =
        runCatching { musicSearch("$title $artist").firstOrNull() }.getOrNull()

    private fun toTrack(item: StreamInfoItem): Track? {
        val id = videoId(item.url) ?: return null
        val art = item.thumbnails.maxByOrNull { it.height }?.url
        return Track(
            id = Track.makeId(SourceType.YOUTUBE, id),
            source = SourceType.YOUTUBE,
            sourceId = id,
            title = item.name,
            artist = cleanArtist(item.uploaderName ?: ""),
            album = "YouTube Music",
            durationMs = item.duration.coerceAtLeast(0) * 1000,
            artworkUrl = art?.let(::squareArt),
            language = LanguageGuess.guess(item.name, item.uploaderName ?: ""),
        )
    }

    private suspend fun musicSearch(query: String): List<Track> = withContext(Dispatchers.IO) {
        ensureInit()
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = videoId(item.url) ?: return@mapNotNull null
                val art = item.thumbnails.maxByOrNull { it.height }?.url
                Track(
                    id = Track.makeId(SourceType.YOUTUBE, id),
                    source = SourceType.YOUTUBE,
                    sourceId = id,
                    title = item.name,
                    artist = cleanArtist(item.uploaderName ?: ""),
                    album = "YouTube Music",
                    durationMs = item.duration.coerceAtLeast(0) * 1000,
                    artworkUrl = art?.let(::squareArt),
                    language = LanguageGuess.guess(item.name, item.uploaderName ?: ""),
                )
            }
            .distinctBy { it.id }
    }

    // ------------------------------------------------------------ YouTube Data API v3 (optional key)

    private object DataApi {
        private const val BASE = "https://www.googleapis.com/youtube/v3"

        suspend fun search(query: String, key: String): List<Track> {
            val url = "$BASE/search".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet")
                .addQueryParameter("type", "video")
                .addQueryParameter("videoCategoryId", "10")
                .addQueryParameter("regionCode", "IN")
                .addQueryParameter("maxResults", "25")
                .addQueryParameter("q", query)
                .addQueryParameter("key", key)
                .build()
            return items(url) { (it["id"] as? JsonObject)?.str("videoId") }
        }

        /** India ke abhi ke top music videos (sirf 1 quota unit). */
        suspend fun mostPopularMusic(key: String): List<Track> {
            val url = "$BASE/videos".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet,contentDetails")
                .addQueryParameter("chart", "mostPopular")
                .addQueryParameter("videoCategoryId", "10")
                .addQueryParameter("regionCode", "IN")
                .addQueryParameter("maxResults", "50")
                .addQueryParameter("key", key)
                .build()
            return items(url) { it.str("id") }
        }

        /** A playlist's name and songs, 50 per page (deleted and private videos left out). */
        suspend fun playlist(id: String, key: String): Pair<String, List<Track>> {
            val meta = "$BASE/playlists".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet").addQueryParameter("id", id).addQueryParameter("key", key).build()
            val name = runCatching {
                val root = Http.json.parseToJsonElement(Http.getText(meta.toString()) ?: "{}") as? JsonObject
                ((root?.get("items") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (it["snippet"] as? JsonObject)?.str("title") }
            }.getOrNull() ?: "YouTube playlist"
            val out = ArrayList<Track>()
            var token: String? = null
            for (page in 0 until 40) {
                val url = "$BASE/playlistItems".toHttpUrl().newBuilder()
                    .addQueryParameter("part", "snippet")
                    .addQueryParameter("maxResults", "50")
                    .addQueryParameter("playlistId", id)
                    .addQueryParameter("key", key)
                    .apply { token?.let { addQueryParameter("pageToken", it) } }
                    .build()
                val body = Http.getText(url.toString()) ?: break
                val root = Http.json.parseToJsonElement(body) as? JsonObject ?: break
                (root["error"] as? JsonObject)?.let { throw IOException(it.str("message") ?: "YouTube API error") }
                (root["items"] as? JsonArray).orEmpty().forEach { el ->
                    val sn = (el as? JsonObject)?.get("snippet") as? JsonObject ?: return@forEach
                    val vid = (sn["resourceId"] as? JsonObject)?.str("videoId") ?: return@forEach
                    val raw = sn.str("title") ?: return@forEach
                    if (raw == "Deleted video" || raw == "Private video") return@forEach
                    // The video's own channel (snippet.channelTitle is whoever made the playlist).
                    track(vid, raw, sn.str("videoOwnerChannelTitle") ?: "", sn["thumbnails"] as? JsonObject, null)?.let(out::add)
                }
                token = (root["nextPageToken"] as? JsonPrimitive)?.contentOrNull ?: break
            }
            return name to out.distinctBy { it.id }
        }

        private fun track(id: String, rawTitle: String, channel: String, thumbs: JsonObject?, duration: String?): Track? {
            val art = listOf("maxres", "standard", "high", "medium", "default")
                .firstNotNullOfOrNull { (thumbs?.get(it) as? JsonObject)?.str("url") }
            val (artist, title) = splitTitle(JioSaavnSource.unescape(rawTitle), cleanArtist(channel.removeSuffix(" - Topic")))
            return Track(
                id = Track.makeId(SourceType.YOUTUBE, id),
                source = SourceType.YOUTUBE,
                sourceId = id,
                title = title,
                artist = artist,
                album = "YouTube",
                durationMs = isoDurationMs(duration),
                artworkUrl = art,
                language = LanguageGuess.guess(rawTitle, channel),
            )
        }

        private suspend fun items(url: HttpUrl, idOf: (JsonObject) -> String?): List<Track> {
            val body = Http.getText(url.toString()) ?: return emptyList()
            val root = Http.json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
            (root["error"] as? JsonObject)?.let { throw IOException(it.str("message") ?: "YouTube API error") }
            return (root["items"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = idOf(o) ?: return@mapNotNull null
                val sn = o["snippet"] as? JsonObject ?: return@mapNotNull null
                val rawTitle = sn.str("title") ?: return@mapNotNull null
                track(id, rawTitle, sn.str("channelTitle") ?: "", sn["thumbnails"] as? JsonObject, (o["contentDetails"] as? JsonObject)?.str("duration"))
            }.distinctBy { it.id }
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        /** "PT3M42S" -> ms */
        private fun isoDurationMs(iso: String?): Long {
            if (iso == null) return 0
            val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(iso) ?: return 0
            val (h, mi, s) = m.destructured
            return ((h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
        }
    }

    companion object {
        /** NewPipe ka apna browser jaisa User-Agent. */
        const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()
        @Volatile private var initialized = false

        fun ensureInit() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                NewPipe.init(NewPipeDownloader, Localization("en", "IN"), ContentCountry("IN"))
                initialized = true
            }
        }

        fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

        /** youtube.com/watch?v=, youtu.be/, music.youtube.com, shorts — sabse video id. */
        fun videoId(url: String): String? {
            val u = url.trim().toHttpUrlOrNull() ?: return null
            val host = u.host.removePrefix("www.").removePrefix("m.")
            val id = when {
                host == "youtu.be" -> u.pathSegments.firstOrNull()
                host.endsWith("youtube.com") && u.pathSegments.firstOrNull() in setOf("shorts", "embed", "live") ->
                    u.pathSegments.getOrNull(1)
                host.endsWith("youtube.com") -> u.queryParameter("v")
                else -> null
            }
            return id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
        }

        /** googlevideo.com ke stream ko wahi User-Agent chahiye jis client se link bana. */
        fun userAgentForStream(url: String): String = when {
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> BROWSER_UA
        }

        private fun cleanArtist(channel: String) = channel
            .removeSuffix(" - Topic").removeSuffix("VEVO").removeSuffix("Official").trim()
            .ifBlank { "YouTube" }

        /**
         * Title saaf karke (artist, gaana) nikaalo.
         *  - Indian format: "Kesariya - Brahmāstra | Ranbir | Arijit Singh | Pritam" -> ("Arijit Singh"?, "Kesariya")
         *  - Western format: "Arijit Singh - Kesariya (Official Video)" -> ("Arijit Singh", "Kesariya")
         */
        private fun splitTitle(raw: String, channel: String): Pair<String, String> {
            val cleaned = raw
                .replace(Regex("""\s*[\(\[][^)\]]*(official|video|lyric|audio|full song|4k|8k|hd|visualizer)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^(video|audio|lyrical|full video)\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""@\S+"""), "")
                .trim()
            if (cleaned.contains(" | ")) {
                val parts = cleaned.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
                // Pehla hissa gaana hai ("Kesariya - Brahmāstra" -> "Kesariya")
                val title = parts.first().substringBefore(" - ").substringBefore(" – ").trim()
                val singer = parts.drop(1).firstOrNull { p -> KNOWN_SINGERS.any { p.contains(it, ignoreCase = true) } }
                return (singer ?: channel) to title.ifBlank { cleaned }
            }
            val sep = listOf(" - ", " – ").firstOrNull { cleaned.contains(it) }
            if (sep != null) {
                val left = cleaned.substringBefore(sep).trim()
                val right = cleaned.substringAfter(sep).trim()
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
            return channel to cleaned
        }

        private val KNOWN_SINGERS = listOf(
            "Arijit", "Shreya Ghoshal", "Atif Aslam", "Jubin", "Neha Kakkar", "Sonu Nigam", "Kishore", "Lata",
            "Honey Singh", "Badshah", "Diljit", "Karan Aujla", "Sidhu Moose", "AP Dhillon", "Shubh", "B Praak",
            "Darshan Raval", "Armaan Malik", "Vishal Mishra", "Pritam", "A.R. Rahman", "Sachet", "Mohit Chauhan",
            "Sunidhi", "KK", "Anuv Jain", "King", "Pawan Singh", "Khesari", "Masoom Sharma", "Guru Randhawa",
        )

        /** Thumbnail ko square cover jaisa (YouTube Music thumbnails pe size badal sakte hain). */
        private fun squareArt(url: String) = url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }
}

/** NewPipeExtractor ke liye OkHttp downloader. */
private object NewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", YouTubeSource.BROWSER_UA)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { res ->
            if (res.code == 429) throw ReCaptchaException("YouTube asked for a captcha", request.url())
            return Response(res.code, res.message, res.headers.toMultimap(), res.body?.string(), res.request.url.toString())
        }
    }
}

/** Title / artist se bhasha ka andaza (Gurmukhi = Punjabi, Devanagari = Hindi, keywords). */
object LanguageGuess {
    /** Garhwali, Kumaoni, Jaunsari and Himachali songs (also written in Devanagari). */
    private val PAHADI = Regex(
        "garhwali|garwali|gadwali|kumaoni|kumauni|kumaon|jaunsari|himachali|pahadi|pahari|uttarakhand|\\bnati\\b|" +
            "गढ़वाली|गढवाली|कुमाऊँनी|कुमाउनी|जौनसारी|पहाड़ी|पहाडी|हिमाचली|" +
            "narendra singh negi|gajendra rana|meena rana|pritam bhartwan|kishan mahipal|inder arya|saurav maithani|" +
            "anisha ranghar|rohit chauhan|kuldeep sharma|thakur das rathi|vicky chauhan|basanti bisht",
        RegexOption.IGNORE_CASE,
    )

    fun isPahadi(title: String, artist: String): Boolean = PAHADI.containsMatchIn("$title $artist")

    fun guess(title: String, artist: String): String {
        val text = "$title $artist"
        if (isPahadi(title, artist)) return "pahadi"
        if (text.any { it in '਀'..'੿' }) return "punjabi"
        if (text.any { it in 'ऀ'..'ॿ' }) return "hindi"
        val t = text.lowercase()
        return when {
            listOf("punjabi", "sidhu", "diljit", "karan aujla", "ap dhillon", "shubh", "gurnam", "jass manak", "ammy virk").any { it in t } -> "punjabi"
            listOf("haryanvi", "masoom sharma", "sapna choudhary").any { it in t } -> "haryanvi"
            listOf("bhojpuri", "pawan singh", "khesari").any { it in t } -> "bhojpuri"
            listOf("hindi", "bollywood", "arijit", "t-series", "shreya ghoshal", "atif aslam", "jubin", "neha kakkar",
                "badshah", "kishore kumar", "lata mangeshkar", "sonu nigam", "zee music", "tips official", "yrf").any { it in t } -> "hindi"
            else -> ""
        }
    }
}
