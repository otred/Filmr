package com.filmr.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim().replace("stremio://", "https://")
                val builder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { res ->
                    if (!res.isSuccessful) null else res.body?.string()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String? {
        return withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val builder = Request.Builder()
                    .url(url.trim())
                    .post(jsonBody.toRequestBody(mediaType))
                    .header("User-Agent", "FilmrTV/6.0")
                    .header("Accept", "application/json")
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { res ->
                    if (!res.isSuccessful) null else res.body?.string()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

object ArabicAutoTranslator {
    private val cache = ConcurrentHashMap<String, String>()

    fun containsArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' }
    }

    suspend fun translateToArabicIfNeeded(text: String, enabled: Boolean): String {
        val trimmed = text.trim()
        if (!enabled || trimmed.isBlank() || containsArabic(trimmed)) return trimmed
        cache[trimmed]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ar&dt=t&q=$encoded"
                val raw = HttpClientProvider.getText(url)
                if (!raw.isNullOrBlank()) {
                    val root = JSONArray(raw)
                    val sentences = root.getJSONArray(0)
                    val sb = StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val part = sentences.getJSONArray(i).optString(0, "")
                        sb.append(part)
                    }
                    val result = sb.toString().trim()
                    if (result.isNotBlank()) {
                        cache[trimmed] = result
                        return@withContext result
                    }
                }
                trimmed
            } catch (_: Exception) {
                trimmed
            }
        }
    }
}
