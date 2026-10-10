package com.sangeet.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sangeet.player.BuildConfig
import com.sangeet.player.data.remote.Http
import com.sangeet.player.data.settings.SettingsRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

data class AppUpdate(val build: Int, val title: String, val notes: String, val assetApiUrl: String, val sizeBytes: Long)

/**
 * App ke andar se update: GitHub ki "latest" release dekho, naya build ho to APK download karke installer kholo.
 * Public repo pe bina token chalta hai; private repo ho to Settings mein read-only GitHub token.
 */
class AppUpdater(private val context: Context, private val settings: SettingsRepository) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val update: AppUpdate) : State
        data class Downloading(val update: AppUpdate, val percent: Int) : State
        data class ReadyToInstall(val file: File) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    /** App khulne par din mein ek-do baar chupchaap check. With "Update automatically" on, the background updater does it. */
    suspend fun checkIfDue() {
        if (BuildConfig.PLAY_STORE) return // Google Play updates the Play version
        if (settings.current.autoUpdate) {
            AutoUpdate.soon(context, 1)
            return
        }
        if (System.currentTimeMillis() - prefs.getLong("last_check", 0L) < 6 * 3_600_000L) return
        check(silent = true)
    }

    /** The newest release (for the background updater), or null. */
    suspend fun latest(): AppUpdate? = fetchLatest().also { prefs.edit().putLong("last_check", System.currentTimeMillis()).apply() }

    /** The APK of [update], downloaded quietly (kept if it's already there). */
    suspend fun apkFor(update: AppUpdate): File {
        val have = File(File(context.cacheDir, "updates"), "Sangeet-${update.build}.apk")
        if (have.exists() && (update.sizeBytes <= 0 || have.length() == update.sizeBytes)) return have
        return fetchApk(update, quiet = true)
    }

    suspend fun check(silent: Boolean = false) {
        if (BuildConfig.PLAY_STORE) return
        if (!silent) _state.value = State.Checking
        _state.value = try {
            val update = fetchLatest()
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            when {
                update == null -> if (silent) State.Idle else State.Error("No release found")
                update.build > currentBuild && update.build != prefs.getInt("skipped", -1) || (!silent && update.build > currentBuild) ->
                    State.Available(update)
                else -> if (silent) State.Idle else State.UpToDate
            }
        } catch (e: Exception) {
            if (silent) State.Idle else State.Error(e.message ?: "Update check failed")
        }
    }

    fun skip(update: AppUpdate) {
        prefs.edit().putInt("skipped", update.build).apply()
        _state.value = State.Idle
    }

    fun dismiss() { _state.value = State.Idle }

    suspend fun download(update: AppUpdate) {
        _state.value = State.Downloading(update, 0)
        _state.value = try {
            State.ReadyToInstall(fetchApk(update))
        } catch (e: Exception) {
            State.Error("Download failed: ${e.message}")
        }
    }

    /** Android ka installer kholo. Pehli baar "Is source se install allow karo" maangega. */
    fun install(file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ------------------------------------------------------------ github

    private fun authed(builder: Request.Builder): Request.Builder = builder.apply {
        header("X-GitHub-Api-Version", "2022-11-28")
        settings.current.githubToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }

    private suspend fun fetchLatest(): AppUpdate? = withContext(Dispatchers.IO) {
        val req = authed(Request.Builder())
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        Http.client.newCall(req).execute().use { res ->
            when (res.code) {
                200 -> Unit
                401, 403 -> throw IOException("GitHub token is invalid or expired")
                404 -> throw IOException(
                    if (settings.current.githubToken.isBlank()) "The repo is private — add a GitHub token in Settings → App update"
                    else "No release found (or the token can't access this repo)"
                )
                else -> throw IOException("GitHub HTTP ${res.code}")
            }
            val o = Http.json.parseToJsonElement(res.body?.string().orEmpty()) as? JsonObject ?: return@use null
            val title = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val notes = (o["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val build = Regex("""build\s+(\d+)""", RegexOption.IGNORE_CASE).find("$title $notes")
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@use null
            val asset = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["name"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk") == true }
                ?: return@use null
            AppUpdate(
                build = build,
                title = title,
                notes = notes,
                assetApiUrl = (asset["url"] as? JsonPrimitive)?.contentOrNull ?: return@use null,
                sizeBytes = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
    }

    private suspend fun fetchApk(update: AppUpdate, quiet: Boolean = false): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Sangeet-${update.build}.apk")
        val part = File(dir, "Sangeet-${update.build}.part")
        // Asset API url -> GitHub storage pe redirect hota hai (OkHttp token wahan nahi bhejta).
        val req = authed(Request.Builder()).url(update.assetApiUrl).header("Accept", "application/octet-stream").build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("Empty file")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            var done = 0L
            var last = -1
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last && !quiet) { last = pct; _state.value = State.Downloading(update, pct) }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(out)) throw IOException("Couldn't save the update")
        out
    }
}
