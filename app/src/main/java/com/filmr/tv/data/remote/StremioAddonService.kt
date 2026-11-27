package com.filmr.tv.data.remote

import com.filmr.tv.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class StremioAddonService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) FilmrTV/1.5")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            res.body?.string()?.let { JSONObject(it) }
        }
    } catch (_: Exception) { null }

    private fun postStremioApi(endpoint: String, payload: JSONObject): JSONObject? = try {
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url("https://api.strem.io/api/$endpoint").post(body).build()
        client.newCall(req).execute().use { res ->
            res.body?.string()?.let { JSONObject(it) }
        }
    } catch (_: Exception) { null }

    fun normalizeManifestUrl(raw: String): String {
        val clean = raw.trim().replace("stremio://", "https://")
        return if (clean.endsWith("/manifest.json")) clean else clean.trimEnd('/') + "/manifest.json"
    }

    suspend fun fetchManifest(rawUrl: String): AddonEntity? = withContext(Dispatchers.IO) {
        val url = normalizeManifestUrl(rawUrl)
        val json = getJson(url) ?: return@withContext null
        val id = json.optString("id").ifBlank { url }
        val name = json.optString("name", "Stremio Addon")
        val desc = json.optString("description", "")
        val logo = json.optString("logo", "")
        AddonEntity(id = id, name = name, url = url, description = desc, logoUrl = logo, type = "stremio")
    }

    suspend fun resolveImdbIdFromCinemeta(type: String, title: String): String = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext ""
        val encoded = URLEncoder.encode(title, "UTF-8")
        val stremioType = if (type == "series") "series" else "movie"
        val json = getJson("https://v3-cinemeta.strem.io/catalog/$stremioType/top/search=$encoded.json") ?: return@withContext ""
        val metas = json.optJSONArray("metas") ?: return@withContext ""
        if (metas.length() > 0) {
            val id = metas.getJSONObject(0).optString("id")
            if (id.startsWith("tt")) return@withContext id
        }
        ""
    }

    suspend fun fetchAllAddonCatalogs(addons: List<AddonEntity>): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        val stremioAddons = addons.filter { it.type == "stremio" && it.isEnabled && !it.url.contains("127.0.0.1") }
        stremioAddons.map { addon ->
            async {
                val rows = mutableListOf<AddonCatalogRow>()
                val base = addon.url.removeSuffix("/manifest.json")
                val manifest = getJson(addon.url) ?: return@async emptyList()
                val catalogs = manifest.optJSONArray("catalogs") ?: return@async emptyList()
                val limit = if (addon.id == "com.linvo.cinemeta") 2 else 3
                for (i in 0 until minOf(catalogs.length(), limit)) {
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
                            if (catType == "series") "مسلسلات شائعة • Cinemeta" else "أفلام شائعة • Cinemeta"
                        } else {
                            "${addon.name} • $catTitle"
                        }
                        rows.add(AddonCatalogRow(addon.id, addon.name, rowHeader, items))
                    }
                }
                rows
            }
        }.awaitAll().flatten()
    }

    suspend fun searchAcrossAddons(addons: List<AddonEntity>, query: String): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val activeAddons = addons.filter { it.type == "stremio" && it.isEnabled && !it.url.contains("127.0.0.1") }
        activeAddons.map { addon ->
            async {
                val rows = mutableListOf<AddonCatalogRow>()
                val base = addon.url.removeSuffix("/manifest.json")
                val manifest = getJson(addon.url) ?: return@async emptyList()
                val catalogs = manifest.optJSONArray("catalogs") ?: return@async emptyList()
                for (i in 0 until catalogs.length()) {
                    val cat = catalogs.getJSONObject(i)
                    val catType = cat.optString("type", "movie")
                    val catId = cat.optString("id")
                    val extraSupported = cat.optJSONArray("extraSupported")?.toString().orEmpty()
                    val extra = cat.optJSONArray("extra")?.toString().orEmpty()
                    if (!extraSupported.contains("search") && !extra.contains("search")) continue
                    val catJson = getJson("$base/catalog/$catType/$catId/search=$encoded.json") ?: continue
                    val metas = catJson.optJSONArray("metas") ?: continue
                    val items = parseMetasArray(metas, catType, addon.name)
                    if (items.isNotEmpty()) {
                        val typeLabel = if (catType == "series") "مسلسلات" else "أفلام"
                        rows.add(AddonCatalogRow(addon.id, addon.name, "${addon.name} ($typeLabel)", items))
                    }
                }
                rows
            }
        }.awaitAll().flatten()
    }

    private fun parseMetasArray(metas: JSONArray, defaultType: String, addonName: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        for (j in 0 until minOf(metas.length(), 18)) {
            val m = metas.getJSONObject(j)
            val id = m.optString("id")
            if (id.isBlank()) continue
            val type = m.optString("type", defaultType)
            val genresList = mutableListOf<String>()
            m.optJSONArray("genres")?.let { g -> for (k in 0 until g.length()) genresList.add(g.getString(k)) }
            items.add(
                MediaItem(
                    id = id,
                    imdbId = if (id.startsWith("tt")) id else "",
                    type = if (type == "series") "series" else "movie",
                    titleAr = m.optString("name"),
                    titleEn = m.optString("name"),
                    posterUrl = m.optString("poster"),
                    backdropUrl = m.optString("background"),
                    logoUrl = m.optString("logo"),
                    overviewAr = m.optString("description"),
                    rating = m.optString("imdbRating").toDoubleOrNull() ?: 0.0,
                    year = m.optString("releaseInfo").take(4),
                    genres = genresList,
                    sourceAddon = addonName
                )
            )
        }
        return items
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, type: String, mediaId: String, torrentEnabled: Boolean): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val stremioType = if (type == "series") "series" else "movie"
        val encodedId = URLEncoder.encode(mediaId, "UTF-8")
        val stremioAddons = addons.filter { it.type == "stremio" && it.isEnabled && !it.url.contains("127.0.0.1") }
        stremioAddons.map { addon ->
            async {
                val base = addon.url.removeSuffix("/manifest.json")
                val json = getJson("$base/stream/$stremioType/$encodedId.json") ?: return@async emptyList()
                val arr = json.optJSONArray("streams") ?: return@async emptyList()
                val list = mutableListOf<UnifiedStream>()
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val directUrl = s.optString("url")
                    val infoHash = s.optString("infoHash")
                    val isTorrent = directUrl.startsWith("magnet:") || infoHash.isNotBlank()
                    if (isTorrent && !torrentEnabled) continue
                    val finalUrl = when {
                        directUrl.isNotBlank() -> directUrl
                        infoHash.isNotBlank() -> "magnet:?xt=urn:btih:$infoHash"
                        else -> ""
                    }
                    if (finalUrl.isBlank()) continue
                    val name = s.optString("name", addon.name)
                    val title = s.optString("title", s.optString("description", name))
                    val combined = "$name $title".lowercase()
                    val (qLabel, qScore) = when {
                        "2160" in combined || "4k" in combined -> "4K" to 2160
                        "1080" in combined -> "1080p" to 1080
                        "720" in combined -> "720p" to 720
                        else -> "HD" to 480
                    }
                    val headers = mutableMapOf<String, String>()
                    s.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")?.let { reqH ->
                        reqH.keys().forEach { k -> headers[k] = reqH.optString(k) }
                    }
                    list.add(UnifiedStream("${addon.id}_$i", name, title, finalUrl, qLabel, qScore, addon.name, "stremio", isTorrent, headers))
                }
                list
            }
        }.awaitAll().flatten().sortedByDescending { it.qualityScore }
    }

    suspend fun fetchSubtitles(addons: List<AddonEntity>, type: String, mediaId: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val stremioType = if (type == "series") "series" else "movie"
        val encodedId = URLEncoder.encode(mediaId, "UTF-8")
        val tracks = mutableListOf<SubtitleTrack>()
        for (addon in addons.filter { it.type == "stremio" && it.isEnabled && !it.url.contains("127.0.0.1") }) {
            val base = addon.url.removeSuffix("/manifest.json")
            val json = getJson("$base/subtitles/$stremioType/$encodedId.json") ?: continue
            val arr = json.optJSONArray("subtitles") ?: continue
            for (i in 0 until arr.length()) {
                val sub = arr.getJSONObject(i)
                val url = sub.optString("url")
                val lang = sub.optString("lang", "ar")
                if (url.isNotBlank()) {
                    tracks.add(SubtitleTrack("${addon.id}_$i", url, lang, "${addon.name} ($lang)"))
                }
            }
        }
        tracks.sortedByDescending { it.lang.startsWith("ar", ignoreCase = true) }
    }

    suspend fun loginStremio(email: String, pass: String): UserSessionEntity? = withContext(Dispatchers.IO) {
        val res = postStremioApi("login", JSONObject().put("email", email.trim()).put("password", pass)) ?: return@withContext null
        val result = res.optJSONObject("result") ?: return@withContext null
        val authKey = result.optString("authKey")
        if (authKey.isBlank()) return@withContext null
        val user = result.optJSONObject("user")
        UserSessionEntity(1, authKey, email.trim(), user?.optString("fullname", email) ?: email, user?.optString("avatar", "") ?: "")
    }

    suspend fun fetchUserAddonsFromAccount(authKey: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        val res = postStremioApi("addonCollectionGet", JSONObject().put("authKey", authKey)) ?: return@withContext emptyList()
        val addons = res.optJSONObject("result")?.optJSONArray("addons") ?: return@withContext emptyList()
        val list = mutableListOf<AddonEntity>()
        for (i in 0 until addons.length()) {
            val obj = addons.getJSONObject(i)
            val url = obj.optString("transportUrl")
            val manifest = obj.optJSONObject("manifest")
            if (url.isNotBlank() && manifest != null) {
                list.add(AddonEntity(manifest.optString("id", url), manifest.optString("name", "Addon"), url, "", manifest.optString("description"), manifest.optString("logo"), "stremio", true, i))
            }
        }
        list
    }

    suspend fun fetchUserLibraryFromAccount(authKey: String): List<LibraryItemEntity> = withContext(Dispatchers.IO) {
        val res = postStremioApi("datastoreGet", JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("all", true)) ?: return@withContext emptyList()
        val arr = res.optJSONArray("result") ?: return@withContext emptyList()
        val list = mutableListOf<LibraryItemEntity>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optBoolean("removed", false)) continue
            val id = item.optString("_id")
            if (id.isBlank()) continue
            list.add(LibraryItemEntity(id, if (id.startsWith("tt")) id else "", 0, item.optString("type", "movie"), item.optString("name"), item.optString("name"), item.optString("poster"), item.optString("background"), "", "", 0.0, item.optString("year"), true))
        }
        list
    }

    suspend fun pushLibraryItemToAccount(authKey: String, item: LibraryItemEntity, remove: Boolean) = withContext(Dispatchers.IO) {
        val change = JSONObject().put("_id", item.id).put("name", item.titleEn.ifBlank { item.titleAr }).put("type", item.type).put("poster", item.posterUrl).put("removed", remove)
        postStremioApi("datastorePut", JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("changes", JSONArray().put(change)))
    }
}
