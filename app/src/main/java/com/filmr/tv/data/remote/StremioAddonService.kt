package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class StremioAddonService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "FilmrTV/2.0 (Android TV)")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            res.body?.string()?.let { JSONObject(it) }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun fetchManifest(rawUrl: String): AddonEntity? = withContext(Dispatchers.IO) {
        val clean = rawUrl.trim().replace("stremio://", "https://")
        val manifestUrl = if (clean.endsWith("/manifest.json")) clean else "${clean.trimEnd('/')}/manifest.json"
        val json = getJson(manifestUrl) ?: return@withContext null
        val id = json.optString("id").ifBlank { manifestUrl.hashCode().toString() }
        val name = json.optString("name", "إضافة Stremio")
        val desc = json.optString("description")
        val logo = json.optString("logo")
        AddonEntity(
            id = id,
            name = name,
            description = desc,
            manifestUrl = manifestUrl,
            logoUrl = logo,
            type = "stremio",
            isEnabled = true
        )
    }

    suspend fun fetchAllAddonCatalogs(addons: List<AddonEntity>): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        val stremioAddons = addons.filter { it.isEnabled && it.type == "stremio" && !it.manifestUrl.contains("127.0.0.1") }
        stremioAddons.map { addon ->
            async {
                withTimeoutOrNull(4500L) {
                    val rows = mutableListOf<AddonCatalogRow>()
                    val base = addon.manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
                    val manifest = getJson(addon.manifestUrl) ?: return@withTimeoutOrNull emptyList()
                    val catalogs = manifest.optJSONArray("catalogs") ?: return@withTimeoutOrNull emptyList()
                    val limit = if (addon.id == "com.linvo.cinemeta") 2 else minOf(3, catalogs.length())
                    for (i in 0 until limit) {
                        val cat = catalogs.getJSONObject(i)
                        val catType = cat.optString("type", "movie")
                        val catId = cat.optString("id")
                        val extra = cat.optJSONArray("extra")
                        var requiresSearch = false
                        if (extra != null) {
                            for (k in 0 until extra.length()) {
                                val eObj = extra.getJSONObject(k)
                                if (eObj.optBoolean("isRequired", false)) requiresSearch = true
                            }
                        }
                        if (catId.isBlank() || requiresSearch) continue
                        val catJson = getJson("$base/catalog/$catType/$catId.json") ?: continue
                        val metas = catJson.optJSONArray("metas") ?: continue
                        val items = parseMetasArray(metas, catType, addon.name)
                        if (items.isNotEmpty()) {
                            val catTitle = cat.optString("name", catId)
                            val rowHeader = if (addon.id == "com.linvo.cinemeta") {
                                if (catType == "series") "مسلسلات رائجة • Cinemeta" else "أفلام رائجة • Cinemeta"
                            } else {
                                "${addon.name} • $catTitle"
                            }
                            rows.add(AddonCatalogRow(addon.id, addon.name, catId, rowHeader, "ar", items))
                        }
                    }
                    rows
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    suspend fun searchAcrossAddons(addons: List<AddonEntity>, query: String): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val activeAddons = addons.filter { it.isEnabled && it.type == "stremio" && !it.manifestUrl.contains("127.0.0.1") }
        activeAddons.map { addon ->
            async {
                withTimeoutOrNull(4500L) {
                    val rows = mutableListOf<AddonCatalogRow>()
                    val base = addon.manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
                    val manifest = getJson(addon.manifestUrl) ?: return@withTimeoutOrNull emptyList()
                    val catalogs = manifest.optJSONArray("catalogs") ?: return@withTimeoutOrNull emptyList()
                    for (i in 0 until minOf(2, catalogs.length())) {
                        val cat = catalogs.getJSONObject(i)
                        val catType = cat.optString("type", "movie")
                        val catId = cat.optString("id")
                        if (catId.isBlank()) continue
                        val catJson = getJson("$base/catalog/$catType/$catId/search=$encoded.json") ?: continue
                        val metas = catJson.optJSONArray("metas") ?: continue
                        val items = parseMetasArray(metas, catType, addon.name)
                        if (items.isNotEmpty()) {
                            rows.add(AddonCatalogRow(addon.id, addon.name, catId, "نتائج ${addon.name}", "ar", items))
                        }
                    }
                    rows
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, item: MediaItem, season: Int, episode: Int): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val active = addons.filter { it.isEnabled && it.type == "stremio" && !it.manifestUrl.contains("127.0.0.1") }
        val stremioType = if (item.type == "series" || item.type == "tv") "series" else "movie"
        val rawId = item.imdbId.ifBlank { item.id }
        if (rawId.isBlank()) return@withContext emptyList()
        val streamId = if (stremioType == "series") "$rawId:$season:$episode" else rawId
        val encodedId = URLEncoder.encode(streamId, "UTF-8")

        active.map { addon ->
            async {
                withTimeoutOrNull(6500L) {
                    val base = addon.manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
                    val json = getJson("$base/stream/$stremioType/$encodedId.json") ?: return@withTimeoutOrNull emptyList()
                    val arr = json.optJSONArray("streams") ?: return@withTimeoutOrNull emptyList()
                    val out = mutableListOf<UnifiedStream>()
                    val nl = 10.toChar().toString()
                    val cr = 13.toChar().toString()
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val infoHash = s.optString("infoHash")
                        val rawUrl = s.optString("url")
                        val finalUrl = when {
                            rawUrl.isNotBlank() -> rawUrl
                            infoHash.isNotBlank() -> "magnet:?xt=urn:btih:$infoHash"
                            else -> ""
                        }
                        if (finalUrl.isBlank()) continue
                        val name = s.optString("name", addon.name).replace(nl, " ").replace(cr, " ")
                        val title = s.optString("title", s.optString("description", "رابط مشاهدة"))
                        val combined = "$name $title"
                        val q = when {
                            combined.contains("2160", true) || combined.contains("4k", true) -> "4K"
                            combined.contains("1080", true) -> "1080p"
                            combined.contains("720", true) -> "720p"
                            else -> "HD"
                        }
                        val score = when (q) {
                            "4K" -> 2160
                            "1080p" -> 1080
                            "720p" -> 720
                            else -> 480
                        }
                        val sizeMatch = Regex("(\d+(?:\.\d+)?\s*(?:GB|MB))", RegexOption.IGNORE_CASE).find(combined)
                        val seedMatch = Regex("👤\s*(\d+)|seeders?:\s*(\d+)", RegexOption.IGNORE_CASE).find(combined)
                        val seeders = (seedMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: seedMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0)
                        val isTorrent = finalUrl.startsWith("magnet:", true) || infoHash.isNotBlank()

                        out.add(
                            UnifiedStream(
                                id = "${addon.id}_$i",
                                name = name,
                                title = title,
                                url = finalUrl,
                                quality = q,
                                qualityScore = score,
                                addonName = addon.name,
                                sourceType = if (isTorrent) "torrent" else "stremio",
                                sizeText = sizeMatch?.value ?: "",
                                seeders = seeders,
                                isTorrent = isTorrent
                            )
                        )
                    }
                    out
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    suspend fun fetchSubtitles(addons: List<AddonEntity>, item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val active = addons.filter { it.isEnabled && it.type == "stremio" && !it.manifestUrl.contains("127.0.0.1") }
        val stremioType = if (item.type == "series" || item.type == "tv") "series" else "movie"
        val rawId = item.imdbId.ifBlank { item.id }
        if (rawId.isBlank()) return@withContext emptyList()
        val subId = if (stremioType == "series") "$rawId:$season:$episode" else rawId
        val encodedId = URLEncoder.encode(subId, "UTF-8")

        active.map { addon ->
            async {
                withTimeoutOrNull(4500L) {
                    val base = addon.manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
                    val json = getJson("$base/subtitles/$stremioType/$encodedId.json") ?: return@withTimeoutOrNull emptyList()
                    val arr = json.optJSONArray("subtitles") ?: return@withTimeoutOrNull emptyList()
                    val out = mutableListOf<SubtitleTrack>()
                    for (i in 0 until arr.length()) {
                        val sub = arr.getJSONObject(i)
                        val url = sub.optString("url")
                        if (url.isBlank()) continue
                        val lang = sub.optString("lang", "ar")
                        val label = when {
                            lang.startsWith("ar", true) -> "العربية (${addon.name})"
                            lang.startsWith("en", true) -> "English (${addon.name})"
                            else -> "$lang (${addon.name})"
                        }
                        out.add(
                            SubtitleTrack(
                                id = "${addon.id}_sub_$i",
                                label = label,
                                language = lang,
                                url = url,
                                addonName = addon.name
                            )
                        )
                    }
                    out
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    private fun parseMetasArray(arr: JSONArray, defaultType: String, addonName: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        for (i in 0 until minOf(25, arr.length())) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id")
            if (id.isBlank()) continue
            val name = m.optString("name")
            val poster = m.optString("poster")
            val bg = m.optString("background")
            val logo = m.optString("logo")
            val desc = m.optString("description")
            val year = m.optString("releaseInfo").take(4)
            val imdbRating = m.optString("imdbRating")
            list.add(
                MediaItem(
                    id = id,
                    imdbId = if (id.startsWith("tt")) id else "",
                    type = m.optString("type", defaultType),
                    titleAr = name,
                    titleEn = name,
                    posterUrl = poster,
                    backdropUrl = bg.ifBlank { poster },
                    logoUrl = logo,
                    overviewAr = desc,
                    year = year,
                    rating = imdbRating.toDoubleOrNull() ?: 0.0,
                    imdbRating = imdbRating,
                    sourceAddon = addonName
                )
            )
        }
        return list
    }
}
