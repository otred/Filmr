package com.filmr.tv.engine

import com.filmr.tv.model.*
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

class StremioEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).header("User-Agent", "FilmrTV/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val body = res.body?.string() ?: return null
            JSONObject(body)
        }
    } catch (_: Exception) { null }

    private fun postStremioApi(endpoint: String, payload: JSONObject): JSONObject? = try {
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url("https://api.strem.io/api/$endpoint").post(body).build()
        client.newCall(req).execute().use { res ->
            val str = res.body?.string() ?: return null
            JSONObject(str)
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
        AddonEntity(id = id, name = name, url = url, description = desc, logoUrl = logo, iconUrl = logo, type = "stremio")
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

    suspend fun fetchAddonCatalogs(addons: List<AddonEntity>): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<AddonCatalogRow>()
        for (addon in addons.filter { it.type == "stremio" && it.id != "com.linvo.cinemeta" && it.id != "org.stremio.opensubtitlesv3" }.take(3)) {
            val base = addon.url.removeSuffix("/manifest.json")
            val manifest = getJson(addon.url) ?: continue
            val catalogs = manifest.optJSONArray("catalogs") ?: continue
            for (i in 0 until minOf(catalogs.length(), 2)) {
                val cat = catalogs.getJSONObject(i)
                val catType = cat.optString("type", "movie")
                val catId = cat.optString("id")
                if (catId.isBlank()) continue
                val catJson = getJson("$base/catalog/$catType/$catId.json") ?: continue
                val metas = catJson.optJSONArray("metas") ?: continue
                val items = mutableListOf<MediaItem>()
                for (j in 0 until minOf(metas.length(), 15)) {
                    val m = metas.getJSONObject(j)
                    val id = m.optString("id")
                    if (id.isBlank()) continue
                    items.add(
                        MediaItem(
                            id = id,
                            imdbId = if (id.startsWith("tt")) id else "",
                            type = if (catType == "series") "series" else "movie",
                            titleAr = m.optString("name"),
                            titleEn = m.optString("name"),
                            posterUrl = m.optString("poster"),
                            backdropUrl = m.optString("background"),
                            overviewAr = m.optString("description"),
                            year = m.optString("releaseInfo")
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    rows.add(AddonCatalogRow("${addon.name} • ${cat.optString("name", catId)}", items))
                }
            }
        }
        rows
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, type: String, mediaId: String, torrentEnabled: Boolean): List<UnifiedStream> = withContext(Dispatchers.IO) {
        val stremioType = if (type == "series") "series" else "movie"
        val encodedId = URLEncoder.encode(mediaId, "UTF-8")
        val stremioAddons = addons.filter { it.type == "stremio" && it.isEnabled }
        val deferred = stremioAddons.map { addon ->
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
                    list.add(UnifiedStream("${addon.id}_$i", name, title, finalUrl, qLabel, qScore, addon.name, isTorrent))
                }
                list
            }
        }
        deferred.awaitAll().flatten().sortedByDescending { it.qualityScore }
    }

    suspend fun fetchSubtitles(addons: List<AddonEntity>, type: String, mediaId: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val stremioType = if (type == "series") "series" else "movie"
        val encodedId = URLEncoder.encode(mediaId, "UTF-8")
        val tracks = mutableListOf<SubtitleTrack>()
        for (addon in addons.filter { it.type == "stremio" && it.isEnabled }) {
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
                list.add(AddonEntity(manifest.optString("id", url), manifest.optString("name", "Addon"), url, manifest.optString("description"), manifest.optString("logo"), manifest.optString("logo"), "stremio", true, i))
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
            list.add(LibraryItemEntity(id, if (id.startsWith("tt")) id else "", 0, item.optString("type", "movie"), item.optString("name"), item.optString("name"), item.optString("poster"), item.optString("background"), "", 0.0, item.optString("year")))
        }
        list
    }

    suspend fun pushLibraryItemToAccount(authKey: String, item: LibraryItemEntity, remove: Boolean) = withContext(Dispatchers.IO) {
        val change = JSONObject().put("_id", item.id).put("name", item.titleEn.ifBlank { item.titleAr }).put("type", item.type).put("poster", item.posterUrl).put("removed", remove)
        postStremioApi("datastorePut", JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("changes", JSONArray().put(change)))
    }
}
