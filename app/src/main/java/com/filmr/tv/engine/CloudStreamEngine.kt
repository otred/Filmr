package com.filmr.tv.engine

import com.filmr.tv.model.AddonEntity
import com.filmr.tv.model.UnifiedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CloudStreamEngine {
    private fun fetchText(urlStr: String): String? = try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)")
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
    } catch (_: Exception) { null }

    suspend fun parseRepository(repoUrl: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        val raw = fetchText(repoUrl.trim()) ?: return@withContext emptyList()
        val out = mutableListOf<AddonEntity>()
        try {
            if (raw.trim().startsWith("[")) parsePluginArray(JSONArray(raw), out)
            else {
                val json = JSONObject(raw)
                json.optJSONArray("pluginLists")?.let { lists ->
                    for (j in 0 until lists.length()) fetchText(lists.optString(j))?.let { parsePluginArray(JSONArray(it), out) }
                }
                json.optJSONArray("plugins")?.let { parsePluginArray(it, out) }
            }
        } catch (_: Exception) { }
        out
    }

    private fun parsePluginArray(arr: JSONArray, out: MutableList<AddonEntity>) {
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val u = p.optString("url")
            if (u.isNotBlank()) out.add(AddonEntity(p.optString("internalName", u.hashCode().toString()), p.optString("name", "CloudStream Plugin"), u, p.optString("description", "ملحق CloudStream"), p.optString("iconUrl", ""), p.optString("iconUrl", ""), "cloudstream", true))
        }
    }

    suspend fun fetchStreamsFromPlugin(addon: AddonEntity, titleEn: String, titleAr: String, imdbId: String, type: String, season: Int, episode: Int): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<UnifiedStream>()
        val cleanBase = addon.url.removeSuffix("/manifest.json").trimEnd('/')
        if (cleanBase.startsWith("http") && !cleanBase.endsWith(".cs3")) {
            val id = if (type == "series") "${imdbId.ifBlank { URLEncoder.encode(titleEn.ifBlank { titleAr }, "UTF-8") }}:$season:$episode" else imdbId.ifBlank { URLEncoder.encode(titleEn.ifBlank { titleAr }, "UTF-8") }
            fetchText("$cleanBase/stream/$type/$id.json")?.let { raw ->
                try {
                    val arr = JSONObject(raw).optJSONArray("streams")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val u = s.optString("url")
                        if (u.isNotBlank()) streams.add(UnifiedStream("${addon.id}_$i", addon.name, s.optString("title", addon.name), u, "1080p", 1080, addon.name))
                    }
                } catch (_: Exception) { }
            }
        }
        streams
    }
}
