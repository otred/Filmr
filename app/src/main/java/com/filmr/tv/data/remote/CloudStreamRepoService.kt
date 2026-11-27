package com.filmr.tv.data.remote

import com.filmr.tv.data.model.AddonEntity
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudStreamRepoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private fun getString(url: String): String? = try {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) null else res.body?.string()
        }
    } catch (_: Exception) {
        null
    }

    suspend fun importRepository(repoUrl: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        val clean = repoUrl.trim()
        val raw = getString(clean) ?: return@withContext emptyList()
        val list = mutableListOf<AddonEntity>()
        try {
            if (raw.trim().startsWith("{")) {
                val obj = JSONObject(raw)
                val repoName = obj.optString("name", "مستودع CloudStream")
                val pluginLists = obj.optJSONArray("pluginLists")
                if (pluginLists != null && pluginLists.length() > 0) {
                    for (i in 0 until pluginLists.length()) {
                        val pUrl = pluginLists.optString(i)
                        if (pUrl.isNotBlank()) {
                            list.addAll(parsePluginsJson(pUrl, repoName))
                        }
                    }
                } else {
                    list.add(
                        AddonEntity(
                            id = "cs_${clean.hashCode()}",
                            name = repoName,
                            description = obj.optString("description", "مستودع إضافات عربي"),
                            manifestUrl = clean,
                            type = "cloudstream",
                            isEnabled = true
                        )
                    )
                }
            } else if (raw.trim().startsWith("[")) {
                list.addAll(parsePluginsArray(JSONArray(raw), "CloudStream", clean))
            }
        } catch (_: Exception) {
        }
        list
    }

    private fun parsePluginsJson(url: String, repoName: String): List<AddonEntity> {
        val raw = getString(url) ?: return emptyList()
        return try {
            parsePluginsArray(JSONArray(raw), repoName, url)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePluginsArray(arr: JSONArray, repoName: String, sourceUrl: String): List<AddonEntity> {
        val out = mutableListOf<AddonEntity>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val name = p.optString("name")
            if (name.isBlank()) continue
            val internal = p.optString("internalName", name)
            val desc = p.optString("description", repoName)
            val icon = p.optString("iconUrl")
            out.add(
                AddonEntity(
                    id = "cs_${internal.hashCode()}",
                    name = name,
                    description = desc,
                    manifestUrl = sourceUrl,
                    logoUrl = icon,
                    type = "cloudstream",
                    isEnabled = true
                )
            )
        }
        return out
    }

    fun buildCloudStreamSources(addons: List<AddonEntity>, item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        val activeCs = addons.filter { it.isEnabled && it.type == "cloudstream" }
        if (activeCs.isEmpty()) return emptyList()
        val queryTitle = item.titleEn.ifBlank { item.titleAr }
        val encoded = URLEncoder.encode(queryTitle, "UTF-8")
        return activeCs.map { addon ->
            UnifiedStream(
                id = "${addon.id}_search",
                name = addon.name,
                title = "بحث مباشر في ${addon.name}: $queryTitle (S${season}E${episode})",
                url = "https://www.google.com/search?q=${encoded}+${URLEncoder.encode(addon.name, "UTF-8")}",
                quality = "HD",
                qualityScore = 720,
                addonName = addon.name,
                sourceType = "cloudstream",
                isTorrent = false
            )
        }
    }
}
