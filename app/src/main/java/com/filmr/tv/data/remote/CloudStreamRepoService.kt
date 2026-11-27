package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import kotlinx.coroutines.*
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
        .followRedirects(true)
        .build()

    private fun fetchText(url: String): String? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            res.body?.string()
        }
    } catch (_: Exception) { null }

    suspend fun importRepository(rawRepoUrl: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        val cleanUrl = rawRepoUrl.trim().split("\s+".toRegex()).firstOrNull() ?: return@withContext emptyList()
        val body = fetchText(cleanUrl) ?: return@withContext emptyList()
        val result = mutableListOf<AddonEntity>()
        try {
            if (body.trimStart().startsWith("[")) {
                parsePluginArray(JSONArray(body), cleanUrl, result, "CloudStream")
            } else {
                val repo = JSONObject(body)
                val repoName = repo.optString("name", "مستودع CloudStream")
                val pluginLists = repo.optJSONArray("pluginLists")
                if (pluginLists != null) {
                    for (i in 0 until pluginLists.length()) {
                        val pUrl = pluginLists.getString(i)
                        val pBody = fetchText(pUrl) ?: continue
                        if (pBody.trimStart().startsWith("[")) {
                            parsePluginArray(JSONArray(pBody), cleanUrl, result, repoName)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        result
    }

    private fun parsePluginArray(arr: JSONArray, repoUrl: String, out: MutableList<AddonEntity>, repoName: String) {
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val name = p.optString("name").ifBlank { p.optString("internalName") }
            val url = p.optString("url")
            val status = p.optInt("status", 1)
            if (name.isNotBlank() && status != 0) {
                val desc = p.optString("description").ifBlank { "إضافة من $repoName" }
                out.add(AddonEntity(
                    id = "cs_${name.lowercase().replace("[^a-z0-9]".toRegex(), "_")}",
                    name = name,
                    description = desc,
                    repoUrl = repoUrl,
                    manifestUrl = url,
                    logoUrl = p.optString("iconUrl"),
                    type = "cloudstream",
                    isEnabled = true,
                    orderIndex = i
                ))
            }
        }
    }

    suspend fun fetchStreamsFromPlugins(
        plugins: List<AddonEntity>,
        item: MediaItem,
        season: Int,
        episode: Int
    ): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val enabled = plugins.filter { it.isEnabled && it.type == "cloudstream" }
        if (enabled.isEmpty()) return@withContext emptyList()

        val qAr = URLEncoder.encode(item.titleAr.ifBlank { item.titleEn }, "UTF-8")
        val out = mutableListOf<UnifiedStream>()

        enabled.take(4).forEachIndexed { idx, plugin ->
            val searchUrl = "https://www.google.com/search?q=${qAr}+${if (item.type == "series") "الحلقة+$episode" else "مترجم"}+${URLEncoder.encode(plugin.name, "UTF-8")}"
            out.add(UnifiedStream(
                id = "${plugin.id}_web_$idx",
                name = "${plugin.name} • بحث مباشر",
                title = "${item.displayTitle} (${plugin.name})",
                url = searchUrl,
                quality = "1080p",
                qualityScore = 1080,
                addonName = plugin.name,
                sourceType = "cloudstream"
            ))
        }
        out
    }
}
