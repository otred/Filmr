package com.filmr.tv.engine

import com.filmr.tv.model.AddonEntity
import com.filmr.tv.model.UnifiedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudStreamEngine {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    suspend fun parseRepository(repoUrl: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(repoUrl.trim()).build()
            val body = client.newCall(req).execute().use { it.body?.string() } ?: return@withContext emptyList()
            val repo = JSONObject(body)
            val pluginsUrls = repo.optJSONArray("pluginLists") ?: return@withContext emptyList()
            val result = mutableListOf<AddonEntity>()
            for (i in 0 until pluginsUrls.length()) {
                val pUrl = pluginsUrls.getString(i)
                val pBody = client.newCall(Request.Builder().url(pUrl).build()).execute().use { it.body?.string() } ?: continue
                val arr = JSONArray(pBody)
                for (j in 0 until arr.length()) {
                    val p = arr.getJSONObject(j)
                    val name = p.optString("name")
                    val url = p.optString("url")
                    if (name.isNotBlank() && url.isNotBlank()) {
                        result.add(AddonEntity("cs_${name.lowercase()}", name, url, p.optString("description", "CloudStream Plugin"), p.optString("iconUrl"), p.optString("iconUrl"), "cloudstream", true, j))
                    }
                }
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchStreamsFromPlugin(addon: AddonEntity, titleEn: String, titleAr: String, imdbId: String, type: String, season: Int, episode: Int): List<UnifiedStream> {
        return emptyList()
    }
}
