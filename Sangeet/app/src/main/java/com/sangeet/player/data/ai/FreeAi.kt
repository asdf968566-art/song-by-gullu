package com.sangeet.player.data.ai

import com.sangeet.player.data.remote.Http
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
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
    const val NAME = "Gemini (with a key), else LLM7.io (GLM, DeepSeek)"
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

    // ------------------------------------------------------------ Google Gemini (free key, owner's choice Oct 9)

    private const val GEMINI = "https://generativelanguage.googleapis.com/v1beta"
    @Volatile private var geminiModel: String? = null

    /**
     * Sends [req] and returns the response. Cancelling the coroutine cancels the call, so when one AI answers the
     * others stop at once instead of holding the DJ up.
     */
    private suspend fun send(req: Request, timeoutMs: Long): Response {
        val client = Http.client.newBuilder().callTimeout(timeoutMs.coerceAtLeast(1_000), TimeUnit.MILLISECONDS).build()
        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }
    }

    private fun version(name: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(name)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** The best Flash model this key may use ("models/gemini-…-flash"), asked once per app run. */
    private suspend fun geminiModelFor(key: String, timeoutMs: Long): String? {
        geminiModel?.let { return it }
        val names = send(Request.Builder().url("$GEMINI/models?pageSize=200&key=$key").build(), timeoutMs).use { res ->
            if (!res.isSuccessful) { android.util.Log.w("Sangeet", "Gemini models: HTTP ${res.code}"); return null }
            (Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject["models"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { m -> (m["supportedGenerationMethods"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.contentOrNull == "generateContent" } }
                .mapNotNull { (it["name"] as? JsonPrimitive)?.contentOrNull }
        }
        val plain = Regex("^models/gemini-\\d+(\\.\\d+)?-flash(-latest)?$")
        // A plain "Flash" (knows songs best for its speed), newest first; else Flash-Lite; else any Flash.
        val pick = names.filter { plain.matches(it) }.maxByOrNull(::version)
            ?: names.filter { it.contains("flash-lite") && !it.contains("preview") }.maxByOrNull(::version)
            ?: names.filter { it.contains("flash") }.maxByOrNull(::version)
        android.util.Log.i("Sangeet", "Gemini model: $pick")
        return pick?.also { geminiModel = it }
    }

    private suspend fun askGemini(key: String, user: String, timeoutMs: Long, system: String = SYSTEM, json: Boolean = true): String? {
        val started = System.currentTimeMillis()
        val model = geminiModelFor(key, timeoutMs) ?: return null
        val left = timeoutMs - (System.currentTimeMillis() - started)
        if (left < 2_000) return null
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } })
            putJsonArray("contents") {
                add(buildJsonObject { put("role", "user"); putJsonArray("parts") { add(buildJsonObject { put("text", user) }) } })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", 0.4); put("maxOutputTokens", 4000)
                if (json) put("responseMimeType", "application/json")
            })
        }
        val req = Request.Builder().url("$GEMINI/$model:generateContent?key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        send(req, left).use { res ->
            if (!res.isSuccessful) {
                // 503 "high demand" is common on the free tier (CI probe, Oct 10).
                android.util.Log.w("Sangeet", "Gemini: HTTP ${res.code}")
                if (res.code == 404) geminiModel = null // the model went away: pick again next time
                return null
            }
            val o = Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
            val parts = ((o["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.let { it["content"] as? JsonObject }?.let { it["parts"] as? JsonArray }.orEmpty()
            return parts.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("")
                .takeIf { it.isNotBlank() }
        }
    }

    /** [block]'s value, or null when it fails (a cancelled coroutine still stops). */
    private inline fun <T> attempt(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("Sangeet", "$what: ${e.message}")
        null
    }

    /**
     * Asks Gemini (with a key) and the keyless models at the same time and returns the first answer [accept] takes.
     * Gemini's free tier takes ~19 s and is often busy (CI probe, Oct 10: thinking less didn't make it faster), and
     * asking one after the other let a slow Gemini use up all the time (owner's report: "Gemini: timeout").
     */
    private suspend fun <T> race(user: String, system: String, json: Boolean, geminiKey: String, timeoutMs: Long, accept: (String) -> T?): T? =
        withContext(Dispatchers.IO) {
            val until = System.currentTimeMillis() + timeoutMs
            val answers = Channel<T?>(Channel.UNLIMITED)
            val askers = buildList {
                if (geminiKey.isNotBlank()) add(launch {
                    val v = attempt("Gemini") { askGemini(geminiKey, user, until - System.currentTimeMillis(), system, json)?.let(accept) }
                    if (v != null) android.util.Log.i("Sangeet", "free AI: Gemini answered")
                    answers.send(v)
                })
                add(launch {
                    var v: T? = null
                    for (p in PROVIDERS) {
                        val left = until - System.currentTimeMillis()
                        if (left < 3_000) break
                        v = attempt("free AI ${p.name}") { ask(p, user, left, system)?.let(accept) }
                        if (v != null) { android.util.Log.i("Sangeet", "free AI: ${p.name} answered"); break }
                    }
                    answers.send(v)
                })
            }
            var got: T? = null
            repeat(askers.size) { if (got == null) got = answers.receive() }
            askers.forEach { it.cancel() }
            got
        }

    /**
     * A plan for [request], or null. [about]: the listener's languages, singers and what's trending. With a Gemini
     * key ([geminiKey]: Settings, else the app's built-in one) Gemini is asked too, at the same time as the others.
     */
    suspend fun plan(request: String, about: String, history: List<String>, geminiKey: String = ""): DjPlan? {
        val earlier = if (history.isEmpty()) "" else
            "Earlier in this chat: " + history.takeLast(4).joinToString(" | ") { "\"${it.take(80)}\"" } +
                ". Treat the request as a change to that mix if it reads like one.\n"
        val user = about.take(500) + "\n" + earlier + "Request: " + request.take(200)
        return race(user, SYSTEM, json = true, geminiKey = geminiKey, timeoutMs = 22_000) { parse(it)?.takeIf { p -> p.songs.isNotEmpty() } }
    }

    /** One keyless model's answer, or null. [timeoutMs] bounds the whole call. */
    private suspend fun ask(p: Provider, user: String, timeoutMs: Long, system: String = SYSTEM): String? {
        val body = buildJsonObject {
            put("model", p.model)
            put("temperature", 0.4)
            put("max_tokens", 3000)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
        }
        val req = Request.Builder().url(p.url)
            .header("Authorization", "Bearer ${p.key}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        send(req, timeoutMs).use { res ->
            if (!res.isSuccessful) {
                android.util.Log.w("Sangeet", "free AI ${p.name}: HTTP ${res.code}")
                return null
            }
            val o = Http.json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
            return (o["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message") as? JsonObject)
                ?.get("content")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }
    }

    // ------------------------------------------------------------ the meaning of a lyrics line (owner, Oct 10)

    private const val MEANING = "You explain Indian song lyrics to listeners. Reply in simple English, in at most 3 short " +
        "sentences, with no heading and no markdown. If the line is not in English (Hindi, Punjabi, Haryanvi or another " +
        "language), first give its English translation in quotes, then what it means in the song."

    /** What [line] of "[title]" by [artist] means, or null (no internet, every AI busy). */
    suspend fun meaning(line: String, title: String, artist: String, geminiKey: String = ""): String? {
        val user = "Song: \"$title\" by ${artist.substringBefore(",")}.\nLine: \"${line.take(300)}\""
        return race(user, MEANING, json = false, geminiKey = geminiKey, timeoutMs = 25_000) {
            it.replace(Regex("(?s)<think>.*?</think>"), "").trim().takeIf { t -> t.isNotBlank() }
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
