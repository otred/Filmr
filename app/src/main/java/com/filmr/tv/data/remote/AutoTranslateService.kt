package com.filmr.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AutoTranslateService {
    private val cache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun containsArabic(text: String): Boolean {
        return text.any { it in '؀'..'ۿ' }
    }

    suspend fun translateToArabicIfNeeded(text: String, enabled: Boolean): String = withContext(Dispatchers.IO) {
        if (!enabled || text.isBlank() || containsArabic(text)) return@withContext text
        cache[text]?.let { return@withContext it }
        try {
            val encoded = URLEncoder.encode(text.take(1400), "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ar&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext text
                val body = res.body?.string() ?: return@withContext text
                val outer = JSONArray(body)
                val sentences = outer.optJSONArray(0) ?: return@withContext text
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val part = sentences.optJSONArray(i)?.optString(0) ?: ""
                    sb.append(part)
                }
                val translated = sb.toString().trim()
                if (translated.isNotBlank()) {
                    cache[text] = translated
                    translated
                } else text
            }
        } catch (_: Exception) {
            text
        }
    }
}
