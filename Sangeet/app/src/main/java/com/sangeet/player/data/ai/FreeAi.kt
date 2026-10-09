package com.sangeet.player.data.ai

import com.sangeet.player.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A free online AI for the DJ when there is no Anthropic key (owner, Oct 9: "free AI like DeepSeek, not ones that give
 * wrong data"). OpenAI-style chat APIs, tried in order; [PROVIDERS] were chosen from CI probes that checked every
 * song a model named on JioSaavn (see CLAUDE.md). Even so the DJ only keeps songs the catalogue really has
 * (AiDj.freeAi). Any failure (busy, offline, bad JSON) gives null and the built-in DJ's mix stays as it is.
 */
object FreeAi {
    data class Provider(val name: String, val url: String, val model: String, val key: String)

    /** Shown in Settings. */
    const val NAME = "LLM7.io (GLM, DeepSeek)"
    private const val LLM7 = "https://api.llm7.io/v1/chat/completions"

    // LLM7.io gives these without a key (CI probe, Oct 9): each phone has its own small daily quota; a busy (503) or
    // used-up (429) model is skipped for the next. GLM-5.2 named real songs (e.g. "Solid Body - KD" for Haryanvi gym);
    // DeepSeek V4 Flash was busy at the time, so it comes second. GitHub Models is gone (models.github.ai only says
    // "OK", the old Azure address no longer exists); Pollinations' model made up song names.
    val PROVIDERS = listOf(
        Provider("GLM-5.2", LLM7, "glm-5.2", "unused"),
        Provider("DeepSeek V4 Flash", LLM7, "DeepSeek-V4-Flash-0731", "unused"),
        Provider("MiniMax M3", LLM7, "minimax-m3", "unused"),
        Provider("Gemma 4", LLM7, "gemma4:31b", "unused"),
    )

    val LANGUAGES = listOf(
        "hindi", "punjabi", "haryanvi", "bhojpuri", "english", "tamil", "telugu", "marathi", "bengali",
        "gujarati", "kannada", "malayalam", "rajasthani", "pahadi",
    )

    private val SYSTEM = "You are the DJ of Sangeet, an Indian music app (JioSaavn and YouTube catalogue). " +
        "Turn the listener's request (English, Hindi or Hinglish) into a playlist. Reply ONLY with JSON: " +
        "{\"title\":str,\"languages\":[str],\"songs\":[\"Song - Singer\"]}. " +
        "languages: lowercase, from ${LANGUAGES.joinToString(", ")}. " +
        "songs: 12 real, released songs that fit, with their exact title and main singer. Never invent a song; " +
        "if you know fewer, give fewer. Mix famous hits with less obvious ones and different singers."

    /** A plan for [request], or null. [about]: the listener's languages, singers and what's trending. */
    suspend fun plan(request: String, about: String, history: List<String>): DjPlan? = withContext(Dispatchers.IO) {
        val earlier = if (history.isEmpty()) "" else
            "Earlier in this chat: " + history.takeLast(4).joinToString(" | ") { "\"${it.take(80)}\"" } +
                ". Treat the request as a change to that mix if it reads like one.\n"
        val user = about.take(500) + "\n" + earlier + "Request: " + request.take(200)
        val until = System.currentTimeMillis() + 22_000
        for (p in PROVIDERS) {
            val left = until - System.currentTimeMillis()
            if (left < 3_000) break
            val text = runCatching { ask(p, user, left) }.getOrNull() ?: continue
            parse(text)?.let {
                android.util.Log.i("Sangeet", "free AI: ${p.name} answered")
                return@withContext it
            }
        }
        null
    }

    /** One model's answer, or null. [timeoutMs] bounds the whole call (a blocking call can't be cancelled). */
    private fun ask(p: Provider, user: String, timeoutMs: Long): String? {
        val body = buildJsonObject {
            put("model", p.model)
            put("temperature", 0.4)
            put("max_tokens", 3000)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", SYSTEM) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
        }
        val req = Request.Builder().url(p.url)
            .header("Authorization", "Bearer ${p.key}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val client = Http.client.newBuilder().callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                android.util.Log.w("Sangeet", "free AI ${p.name}: HTTP ${res.code}")
                return null
            }
            val o = Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
            return (o["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message") as? JsonObject)
                ?.get("content")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }
    }

    /** The JSON plan inside [body] (it can come wrapped in ``` fences or text), or null. */
    fun parse(body: String): DjPlan? = runCatching {
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = Http.json.parseToJsonElement(body.substring(start, end + 1)).jsonObject
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        fun list(k: String) = (o[k] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.length in 2..90 }
        val plan = DjPlan().apply {
            title = str("title").take(48)
            languages = list("languages").map { it.lowercase() }.filter { it in LANGUAGES }.distinct()
            // "Song - Singer", or {"title": .., "artist": ..} (some models answer so).
            songs = (o["songs"] as? JsonArray).orEmpty().mapNotNull { e ->
                (e as? JsonPrimitive)?.contentOrNull?.trim() ?: (e as? JsonObject)?.let { x ->
                    val t = (x["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    val a = ((x["artist"] ?: x["singer"]) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    if (t.isEmpty()) null else if (a.isEmpty()) t else "$t - $a"
                }
            }.filter { it.length in 2..90 }.distinct().take(20)
        }
        plan.takeIf { it.songs.isNotEmpty() }
    }.getOrNull()

    /** "Kesariya - Arijit Singh" -> ("Kesariya", "Arijit Singh"); no singer -> ("Kesariya", ""). */
    fun split(song: String): Pair<String, String> {
        val parts = song.split(Regex("\\s+[-–—]\\s+|\\s+by\\s+"), limit = 2)
        return parts[0].trim().trim('"') to parts.getOrElse(1) { "" }.trim()
    }

    /** Lowercase letters and digits only, without "(From ...)" / "[Remix]" parts. */
    fun norm(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
