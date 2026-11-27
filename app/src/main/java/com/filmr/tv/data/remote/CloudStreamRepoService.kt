package com.filmr.tv.data.remote

import com.filmr.tv.domain.model.AddonEntity
import com.filmr.tv.domain.model.UnifiedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudStreamRepoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun fetchText(url: String): String? = try {
        val clean = url.trim().split("\\s+".toRegex()).firstOrNull { it.startsWith("http") } ?: return null
        val req = Request.Builder()
            .url(clean)
            .header("User-Agent", "CloudStream/3.0 FilmrTV/1.5")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) null else res.body?.string()
        }
    } catch (_: Exception) { null }

    suspend fun parseRepository(rawRepoUrl: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        val cleanUrl = rawRepoUrl.trim().split("\\s+".toRegex()).firstOrNull { it.startsWith("http") } ?: return@withContext emptyList()
        val body = fetchText(cleanUrl) ?: return@withContext emptyList()
        val result = mutableListOf<AddonEntity>()
        try {
            if (body.trimStart().startsWith("[")) {
                parsePluginArray(JSONArray(body), cleanUrl, result)
            } else {
                val repo = JSONObject(body)
                val repoName = repo.optString("name", "CloudStream Repo")
                val pluginsUrls = repo.optJSONArray("pluginLists")
                if (pluginsUrls != null) {
                    for (i in 0 until pluginsUrls.length()) {
                        val pUrl = pluginsUrls.getString(i)
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

    private fun parsePluginArray(arr: JSONArray, repoUrl: String, out: MutableList<AddonEntity>, repoName: String = "") {
        for (j in 0 until arr.length()) {
            val p = arr.optJSONObject(j) ?: continue
            val name = p.optString("name").ifBlank { p.optString("internalName") }
            val url = p.optString("url")
            val status = p.optInt("status", 1)
            if (name.isNotBlank() && url.isNotBlank() && status != 0) {
                val desc = p.optString("description").ifBlank { if (repoName.isNotBlank()) "إضافة $repoName" else "إضافة CloudStream" }
                out.add(
                    AddonEntity(
                        id = "cs_${name.lowercase().replace("[^a-z0-9]".toRegex(), "_")}_$j",
                        name = name,
                        url = url,
                        repoUrl = repoUrl,
                        description = desc,
                        logoUrl = p.optString("iconUrl"),
                        type = "cloudstream",
                        isEnabled = true,
                        orderIndex = j
                    )
                )
            }
        }
    }

    suspend fun fetchStreamsFromPlugins(
        plugins: List<AddonEntity>,
        titleEn: String,
        titleAr: String,
        imdbId: String,
        type: String,
        season: Int,
        episode: Int
    ): List<UnifiedStream> = withContext(Dispatchers.IO) {
        emptyList()
    }
}
