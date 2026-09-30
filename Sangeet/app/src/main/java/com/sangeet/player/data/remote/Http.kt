package com.sangeet.player.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object Http {
    const val USER_AGENT = "Sangeet/1.0 (Android music player)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    /** GET karke body text lautata hai. 404 pe null. */
    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            when {
                res.code == 404 -> null
                !res.isSuccessful -> throw IOException("HTTP ${res.code}")
                else -> res.body?.string()
            }
        }
    }
}
