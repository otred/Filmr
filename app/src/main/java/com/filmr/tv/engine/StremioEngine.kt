package com.filmr.tv.engine

import com.filmr.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StremioEngine {
    private val defaultTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.pw:1337/announce"
    ).joinToString("") { "&tr=${URLEncoder.encode(it, "UTF-8")}" }

    private fun getJson(urlStr: String): JSONObject? = try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (conn.responseCode in 200..299) JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) else null
    } catch (_: Exception) { null }

    suspend fun resolveImdbIdFromCinemeta(type: String, title: String): String = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext ""
        val q = URLEncoder.encode(title, "UTF-8")
        val json = getJson("https://v3-cinemeta.strem.io/catalog/$type/top/search=$q.json") ?: return@withContext ""
        val metas = json.optJSONArray("metas") ?: return@withContext ""
        if (metas.length() > 0) metas.getJSONObject(0).let { it.optString("imdb_id", it.optString("id", "")) } else ""
    }

    suspend fun fetchManifest(manifestUrl: String): AddonEntity? = withContext(Dispatchers.IO) {
        val clean = manifestUrl.trim().replace("stremio://", "https://").let {
            if (it.endsWith("manifest.json")) it else "${it.trimEnd('/')}/manifest.json"
        }
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

    suspend fun fetchAddonCatalogs(addons: List<AddonEntity>): List<AddonCatalogRow> = coroutineScope {
        val customAddons = addons.filter { it.isEnabled && it.type == "stremio" && !it.url.contains("cinemeta") && !it.url.contains("opensubtitles") }
        customAddons.take(4).map { addon ->
            async(Dispatchers.IO) {
                val manifest = getJson(addon.url) ?: return@async emptyList<AddonCatalogRow>()
                val catalogs = manifest.optJSONArray("catalogs") ?: return@async emptyList<AddonCatalogRow>()
                val rows = mutableListOf<AddonCatalogRow>()
                val root = addon.url.removeSuffix("/manifest.json").trimEnd('/')
                for (i in 0 until minOf(catalogs.length(), 2)) {
                    val cat = catalogs.getJSONObject(i)
                    val type = cat.optString("type", "movie")
                    val id = cat.optString("id")
                    val name = cat.optString("name", addon.name)
                    if (id.isBlank()) continue
                    val data = getJson("$root/catalog/$type/$id.json") ?: continue
                    val metas = data.optJSONArray("metas") ?: continue
                    val items = List(minOf(metas.length(), 15)) { j ->
                        val m = metas.getJSONObject(j)
                        val mid = m.optString("id")
                        MediaItem(
                            id = mid,
                            imdbId = if (mid.startsWith("tt")) mid else m.optString("imdb_id", ""),
                            type = m.optString("type", type),
                            titleAr = m.optString("name", ""),
                            titleEn = m.optString("name", ""),
                            posterUrl = m.optString("poster", ""),
                            backdropUrl = m.optString("background", m.optString("poster", "")),
                            overviewAr = m.optString("description", ""),
                            rating = m.optString("imdbRating", "0.0").toDoubleOrNull() ?: 0.0,
                            year = m.optString("releaseInfo", "")
                        )
                    }
                    if (items.isNotEmpty()) rows.add(AddonCatalogRow("${addon.name} - $name", items))
                }
                rows
            }
        }.awaitAll().flatten()
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, type: String, mediaId: String, allowTorrents: Boolean = true): List<UnifiedStream> = coroutineScope {
        addons.filter { it.isEnabled && it.type == "stremio" }.map { addon ->
            async(Dispatchers.IO) {
                val root = addon.url.removeSuffix("/manifest.json").trimEnd('/')
                val encodedId = URLEncoder.encode(mediaId, "UTF-8").replace("%3A", ":")
                val json = getJson("$root/stream/$type/$encodedId.json") ?: return@async emptyList()
                val arr = json.optJSONArray("streams") ?: return@async emptyList()
                val list = mutableListOf<UnifiedStream>()
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val directUrl = s.optString("url")
                    val infoHash = s.optString("infoHash")
                    val extUrl = s.optString("externalUrl")
                    val isTorrent = directUrl.startsWith("magnet:") || infoHash.isNotBlank()
                    if (isTorrent && !allowTorrents) continue
                    val finalUrl = when {
                        directUrl.isNotBlank() -> directUrl
                        infoHash.isNotBlank() -> "magnet:?xt=urn:btih:$infoHash&dn=${URLEncoder.encode(s.optString("title", "video"), "UTF-8")}$defaultTrackers"
                        extUrl.isNotBlank() -> extUrl
                        else -> continue
                    }
                    val title = s.optString("title", s.optString("description", "Stream"))
                    val name = s.optString("name", addon.name)
                    val qScore = when { title.contains("2160") || title.contains("4K", true) -> 2160; title.contains("1080") -> 1080; title.contains("720") -> 720; else -> 480 }
                    val subsArr = s.optJSONArray("subtitles") ?: JSONArray()
                    val subs = List(subsArr.length()) { j ->
                        val sub = subsArr.getJSONObject(j)
                        SubtitleTrack(sub.optString("id", "$j"), sub.optString("url"), sub.optString("lang", "ar"), sub.optString("lang", "Arabic"))
                    }
                    list.add(UnifiedStream("${addon.id}_$i", name, title, finalUrl, "${qScore}p", qScore, addon.name, isTorrent, subs))
                }
                list
            }
        }.awaitAll().flatten().sortedWith(compareBy<UnifiedStream> { it.isTorrent }.thenByDescending { it.qualityScore })
    }

    suspend fun fetchSubtitles(addons: List<AddonEntity>, type: String, mediaId: String): List<SubtitleTrack> = coroutineScope {
        val encodedId = URLEncoder.encode(mediaId, "UTF-8").replace("%3A", ":")
        addons.filter { it.isEnabled && it.type == "stremio" }.map { addon ->
            async(Dispatchers.IO) {
                val root = addon.url.removeSuffix("/manifest.json").trimEnd('/')
                val json = getJson("$root/subtitles/$type/$encodedId.json") ?: return@async emptyList()
                val arr = json.optJSONArray("subtitles") ?: return@async emptyList()
                List(arr.length()) { i ->
                    val sub = arr.getJSONObject(i)
                    val url = sub.optString("url")
                    val lang = sub.optString("lang", "ar")
                    if (url.isNotBlank()) SubtitleTrack("${addon.id}_$i", url, lang, "${addon.name} ($lang)") else null
                }.filterNotNull()
            }
        }.awaitAll().flatten()
    }

    suspend fun loginStremio(email: String, password: String): UserSessionEntity? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.strem.io/api/login").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().put("email", email).put("password", password).toString()) }
            val res = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONObject("result") ?: return@withContext null
            val key = res.optString("authKey").takeIf { it.isNotBlank() } ?: return@withContext null
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
            OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().put("type", "AddonCollectionGet").put("authKey", authKey).put("update", true).toString()) }
            val addons = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONObject("result")?.optJSONArray("addons") ?: return@withContext emptyList()
            List(addons.length()) { i ->
                val item = addons.getJSONObject(i)
                val m = item.optJSONObject("manifest") ?: JSONObject()
                val tUrl = item.optString("transportUrl")
                AddonEntity(m.optString("id", tUrl.hashCode().toString()), m.optString("name", "Addon"), tUrl, m.optString("description", ""), m.optString("logo", ""), m.optString("logo", ""), "stremio", true, i)
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchUserLibraryFromAccount(authKey: String): List<LibraryItemEntity> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.strem.io/api/datastoreGet").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("all", true).toString()) }
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("result") ?: return@withContext emptyList()
            val list = mutableListOf<LibraryItemEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optBoolean("removed", false)) continue
                val id = obj.optString("_id")
                val name = obj.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    list.add(LibraryItemEntity(id, if (id.startsWith("tt")) id else "", 0, obj.optString("type", "movie"), name, name, obj.optString("poster", ""), obj.optString("background", obj.optString("poster", "")), "", 0.0, obj.optString("year", "")))
                }
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    suspend fun pushLibraryItemToAccount(authKey: String, item: LibraryItemEntity, removed: Boolean) = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.strem.io/api/datastorePut").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val change = JSONObject().put("_id", item.imdbId.ifBlank { item.id }).put("name", item.titleEn.ifBlank { item.titleAr }).put("type", item.type).put("poster", item.posterUrl).put("background", item.backdropUrl).put("removed", removed).put("temp", false)
            OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("changes", JSONArray().put(change)).toString()) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { }
    }
}
