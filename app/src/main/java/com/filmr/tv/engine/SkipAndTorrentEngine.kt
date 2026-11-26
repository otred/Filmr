package com.filmr.tv.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SkipSegment(val label: String, val type: String, val startMs: Long, val endMs: Long)

object SkipAndTorrentEngine {
    suspend fun fetchSkipSegments(
        imdbId: String,
        season: Int,
        episode: Int,
        animeSkipClientId: String = "tQy0yTzaDFIYmxpyXsdRKroBhGem6pdg"
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<SkipSegment>()
        if (imdbId.isBlank()) return@withContext segments
        try {
            val conn = URL("https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (animeSkipClientId.isNotBlank()) conn.setRequestProperty("X-Client-ID", animeSkipClientId)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val type = obj.optString("type", "intro")
                    val lbl = when (type) { "recap" -> "الملخص"; "outro", "credits" -> "النهاية"; else -> "المقدمة" }
                    val start = (obj.optDouble("start_sec", 0.0) * 1000).toLong()
                    val end = (obj.optDouble("end_sec", 0.0) * 1000).toLong()
                    if (end > start) segments.add(SkipSegment(lbl, type, start, end))
                }
            }
        } catch (_: Exception) { }

        if (segments.isEmpty() && animeSkipClientId.isNotBlank()) {
            try {
                val conn = URL("https://api.anime-skip.com/graphql").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Client-ID", animeSkipClientId)
                conn.doOutput = true
                val q = JSONObject().put("query", "{ searchShows(search: \"$imdbId\") { id } }").toString()
                OutputStreamWriter(conn.outputStream).use { it.write(q) }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) { }
        }
        segments
    }
}
