package com.filmr.tv.data.remote

import com.filmr.tv.data.model.AddonEntity
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudStreamRepoService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchJsonElement(url: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "CloudStream/3.4.0 (FilmrTV)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JsonParser.parseString(body)
            }
        }.getOrNull()
    }

    /**
     * Installs CloudStream repositories (repo.json containing pluginLists) or direct plugins.json arrays
     * Completely separated from Stremio addons (`sourceType = "cloudstream"`).
     */
    suspend fun installCloudStreamRepo(repoUrl: String, baseOrder: Int = 100): List<AddonEntity> {
        val cleanUrl = repoUrl.trim()
        val rootEl = fetchJsonElement(cleanUrl) ?: return emptyList()
        val installed = mutableListOf<AddonEntity>()

        if (rootEl.isJsonObject) {
            val obj = rootEl.asJsonObject
            val repoName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "CloudStream Repo"
            val pluginLists = obj.getAsJsonArray("pluginLists")
            if (pluginLists != null && pluginLists.size() > 0) {
                pluginLists.forEach { listUrlEl ->
                    val listUrl = listUrlEl.asString
                    val pluginsEl = fetchJsonElement(listUrl)
                    if (pluginsEl != null && pluginsEl.isJsonArray) {
                        installed.addAll(parsePluginsArray(pluginsEl.asJsonArray, listUrl, repoName, baseOrder + installed.size))
                    }
                }
            } else if (obj.has("url") && obj.has("name")) {
                parseSinglePlugin(obj, cleanUrl, repoName, baseOrder)?.let { installed.add(it) }
            }
        } else if (rootEl.isJsonArray) {
            installed.addAll(parsePluginsArray(rootEl.asJsonArray, cleanUrl, "CloudStream", baseOrder))
        }

        return installed
    }

    private fun parsePluginsArray(
        arr: com.google.gson.JsonArray,
        sourceUrl: String,
        repoName: String,
        startOrder: Int
    ): List<AddonEntity> {
        val list = mutableListOf<AddonEntity>()
        arr.forEachIndexed { idx, el ->
            if (el.isJsonObject) {
                parseSinglePlugin(el.asJsonObject, sourceUrl, repoName, startOrder + idx)?.let {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun parseSinglePlugin(
        obj: JsonObject,
        sourceUrl: String,
        repoName: String,
        orderIndex: Int
    ): AddonEntity? {
        val internalName = obj.get("internalName")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("name")?.takeIf { !it.isJsonNull }?.asString
            ?: return null
        val displayName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: internalName
        val desc = obj.get("description")?.takeIf { !it.isJsonNull }?.asString
            ?: "إضافة CloudStream ($repoName)"
        val pluginUrl = obj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: sourceUrl
        val icon = obj.get("iconUrl")?.takeIf { !it.isJsonNull }?.asString
            ?.replace("%size%", "128")
        val version = obj.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "1.0"
        val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asInt ?: 1
        if (status == 0) return null // Disabled/broken plugin in repo

        return AddonEntity(
            id = "cs_${internalName.lowercase().replace("[^a-z0-9_]".toRegex(), "_")}",
            name = displayName,
            description = desc,
            manifestUrl = sourceUrl,
            transportUrl = pluginUrl,
            version = version,
            iconUrl = icon,
            sourceType = "cloudstream",
            isFromStremioAccount = false,
            enabled = true,
            orderIndex = orderIndex,
            rawManifestJson = obj.toString()
        )
    }

    /**
     * Queries enabled CloudStream extensions and extracts streams / direct mirrors / torrent links
     * to merge cleanly inside the Nuvio-style Unified Server Picker alongside Stremio streams!
     */
    suspend fun fetchStreamsForCloudStreamPlugin(
        plugin: AddonEntity,
        media: MediaItem,
        season: Int? = null,
        episode: Int? = null
    ): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<UnifiedStream>()
        val queryTitle = media.titleEn.ifBlank { media.titleAr }
        val encodedQuery = URLEncoder.encode(queryTitle, "UTF-8")

        // 1. Check if plugin exposes a compatible Stremio/JSON bridge or direct site API
        val rawMeta = runCatching { JsonParser.parseString(plugin.rawManifestJson).asJsonObject }.getOrNull()
        val apiUrl = rawMeta?.get("apiUrl")?.takeIf { !it.isJsonNull }?.asString
            ?: rawMeta?.get("repositoryUrl")?.takeIf { !it.isJsonNull }?.asString

        if (!apiUrl.isNullOrBlank() && apiUrl.startsWith("http")) {
            val probeUrl = if (season != null && episode != null) {
                "${apiUrl.trimEnd('/')}/stream/series/${media.imdbId ?: media.id}:$season:$episode.json"
            } else {
                "${apiUrl.trimEnd('/')}/stream/movie/${media.imdbId ?: media.id}.json"
            }
            val resp = fetchJsonElement(probeUrl)
            if (resp != null && resp.isJsonObject) {
                resp.asJsonObject.getAsJsonArray("streams")?.forEachIndexed { idx, sEl ->
                    val so = sEl.asJsonObject
                    val u = so.get("url")?.takeIf { !it.isJsonNull }?.asString
                    val hash = so.get("infoHash")?.takeIf { !it.isJsonNull }?.asString
                    if (!u.isNullOrBlank() || !hash.isNullOrBlank()) {
                        val isTor = !hash.isNullOrBlank() || (u?.startsWith("magnet:") == true)
                        streams.add(
                            UnifiedStream(
                                id = "${plugin.id}_cs_$idx",
                                title = so.get("name")?.asString ?: plugin.name,
                                description = so.get("title")?.asString ?: "$queryTitle • CloudStream",
                                url = u ?: "magnet:?xt=urn:btih:$hash",
                                infoHash = hash,
                                quality = "1080p",
                                isTorrent = isTor,
                                sourceEngine = "cloudstream",
                                addonName = plugin.name,
                                addonUrl = plugin.transportUrl
                            )
                        )
                    }
                }
            }
        }

        // 2. Provide direct CloudStream provider search/stream entry if no direct JSON endpoint was exposed
        if (streams.isEmpty()) {
            val epTag = if (season != null && episode != null) " S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}" else ""
            val searchTarget = if (!apiUrl.isNullOrBlank() && apiUrl.startsWith("http")) {
                "${apiUrl.trimEnd('/')}/?s=$encodedQuery"
            } else {
                "https://www.google.com/search?q=${URLEncoder.encode("${plugin.name} $queryTitle$epTag watch online", "UTF-8")}"
            }
            streams.add(
                UnifiedStream(
                    id = "${plugin.id}_cs_provider",
                    title = "${plugin.name} (CloudStream)",
                    description = "بحث وتشغيل عبر مزود CloudStream: $queryTitle$epTag",
                    url = searchTarget,
                    quality = "HD / Multi",
                    isTorrent = false,
                    sourceEngine = "cloudstream",
                    addonName = plugin.name,
                    addonUrl = plugin.transportUrl
                )
            )
        }

        streams
    }
}
