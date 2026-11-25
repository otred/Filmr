package com.filmr.tv.engine

import com.filmr.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class StremioEngine {
    private fun getJson(urlStr: String): JSONObject? = try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(text)
    } catch (_: Exception) { null }

    suspend fun fetchManifest(manifestUrl: String): AddonEntity? = withContext(Dispatchers.IO) {
        val clean = if (manifestUrl.endsWith("manifest.json")) manifestUrl else "${manifestUrl.trimEnd('/')}/manifest.json"
        val json = getJson(clean) ?: return@withContext null
        AddonEntity(
            id = json.optString("id", clean.hashCode().toString()),
            name = json.optString("name", "Stremio Addon"),
            url = clean,
            description = json.optString("description", ""),
            logoUrl = json.optString("logo", ""),
            iconUrl = json.optString("icon", json.optString("logo", "")),
            type = "stremio",
            isEnabled = true
        )
    }

    suspend fun fetchCatalog(baseUrl: String, type: String, catalogId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val root = baseUrl.removeSuffix("/manifest.json").trimEnd('/')
        val json = getJson("$root/catalog/$type/$catalogId.json") ?: return@withContext emptyList()
        val metas = json.optJSONArray("metas") ?: return@withContext emptyList()
        List(metas.length()) { i ->
            val m = metas.getJSONObject(i)
            val id = m.optString("id")
            MediaItem(
                id = id,
                imdbId = if (id.startsWith("tt")) id else m.optString("imdb_id", ""),
                type = m.optString("type", type),
                titleEn = m.optString("name", ""),
                titleAr = m.optString("name", ""),
                posterUrl = m.optString("poster", ""),
                backdropUrl = m.optString("background", m.optString("poster", "")),
                overviewEn = m.optString("description", ""),
                year = m.optString("releaseInfo", ""),
                rating = m.optString("imdbRating", "0.0").toDoubleOrNull() ?: 0.0
            )
        }
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, type: String, mediaId: String): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val out = mutableListOf<UnifiedStream>()
        for (addon in addons.filter { it.isEnabled }) {
            val root = addon.url.removeSuffix("/manifest.json").trimEnd('/')
            val json = getJson("$root/stream/$type/$mediaId.json") ?: continue
            val arr = json.optJSONArray("streams") ?: continue
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val url = s.optString("url")
                if (url.isBlank()) continue
                val title = s.optString("title", s.optString("description", "Stream"))
                val name = s.optString("name", addon.name)
                val qScore = when {
                    title.contains("2160") || title.contains("4K", true) -> 2160
                    title.contains("1080") -> 1080
                    title.contains("720") -> 720
                    else -> 480
                }
                val subsArr = s.optJSONArray("subtitles") ?: JSONArray()
                val subs = List(subsArr.length()) { j ->
                    val sub = subsArr.getJSONObject(j)
                    SubtitleTrack(sub.optString("id", "$j"), sub.optString("url"), sub.optString("lang", "ar"), sub.optString("lang", "Arabic"))
                }
                out.add(UnifiedStream("${addon.id}_$i", name, title, url, "${qScore}p", qScore, addon.name, url.startsWith("magnet:"), subs))
            }
        }
        out.sortedByDescending { it.qualityScore }
    }

    suspend fun fetchSubtitles(addons: List<AddonEntity>, type: String, mediaId: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SubtitleTrack>()
        for (addon in addons.filter { it.isEnabled }) {
            val root = addon.url.removeSuffix("/manifest.json").trimEnd('/')
            val json = getJson("$root/subtitles/$type/$mediaId.json") ?: continue
            val arr = json.optJSONArray("subtitles") ?: continue
            for (i in 0 until arr.length()) {
                val sub = arr.getJSONObject(i)
                val url = sub.optString("url")
                if (url.isNotBlank()) out.add(SubtitleTrack(sub.optString("id", "$i"), url, sub.optString("lang", "ar"), sub.optString("lang", "Arabic")))
            }
        }
        out
    }

    suspend fun loginStremio(email: String, password: String): UserSessionEntity? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.strem.io/api/login").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = JSONObject().put("email", email).put("password", password).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val res = resp.optJSONObject("result") ?: return@withContext null
            val key = res.optString("authKey")
            if (key.isBlank()) return@withContext null
            val user = res.optJSONObject("user")
            UserSessionEntity(1, key, email, user?.optString("fullname", email) ?: email, user?.optString("avatar", "") ?: "")
        } catch (_: Exception) { null }
    }

    suspend fun fetchUserAddonsFromAccount(authKey: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.strem.io/api/addonCollectionGet").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = JSONObject().put("type", "AddonCollectionGet").put("authKey", authKey).put("update", true).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val addons = resp.optJSONObject("result")?.optJSONArray("addons") ?: return@withContext emptyList()
            List(addons.length()) { i ->
                val item = addons.getJSONObject(i)
                val m = item.optJSONObject("manifest") ?: JSONObject()
                val tUrl = item.optString("transportUrl")
                AddonEntity(
                    id = m.optString("id", tUrl.hashCode().toString()),
                    name = m.optString("name", "Addon"),
                    url = tUrl,
                    description = m.optString("description", ""),
                    logoUrl = m.optString("logo", ""),
                    iconUrl = m.optString("logo", ""),
                    type = "stremio",
                    isEnabled = true
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
