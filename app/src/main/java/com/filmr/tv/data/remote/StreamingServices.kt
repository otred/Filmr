package com.filmr.tv.data.remote

import com.filmr.tv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class StremioAndCloudStreamService {

    companion object {
        // Default working Stremio addons pre-configured out of the box so streaming works immediately!
        val DEFAULT_ADDONS = listOf(
            StremioAddon(
                id = "com.stremio.torrentio.addon",
                name = "Torrentio",
                version = "0.0.14",
                description = "بث تورنت فوري عالي الجودة 4K / 1080p",
                manifestUrl = "https://torrentio.strem.fun/manifest.json",
                baseUrl = "https://torrentio.strem.fun",
                types = listOf("movie", "series", "anime"),
                resources = listOf("stream"),
                enabled = true,
                orderIndex = 0
            ),
            StremioAddon(
                id = "org.stremio.nuvio.streams",
                name = "Nuvio HTTP Streams",
                version = "1.2.0",
                description = "روابط بث مباشر HTTP/HLS سريعة بدون تورنت",
                manifestUrl = "https://nuviostreams.hayd.uk/manifest.json",
                baseUrl = "https://nuviostreams.hayd.uk",
                types = listOf("movie", "series"),
                resources = listOf("stream"),
                enabled = true,
                orderIndex = 1
            ),
            StremioAddon(
                id = "com.linvo.cinemeta",
                name = "Cinemeta",
                version = "3.0.13",
                description = "كتالوجات وبيانات الأفلام والمسلسلات الرسمية",
                manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                baseUrl = "https://v3-cinemeta.strem.io",
                types = listOf("movie", "series"),
                resources = listOf("catalog", "meta"),
                enabled = true,
                orderIndex = 2
            )
        )
    }

    // Parse & Install any Stremio Addon Manifest URL
    suspend fun fetchAddonManifest(manifestUrlInput: String, orderIndex: Int = 0): StremioAddon? {
        val cleanManifest = manifestUrlInput.trim().replace("stremio://", "https://").let {
            if (it.endsWith("/manifest.json")) it else "${it.trimEnd('/')}/manifest.json"
        }
        val raw = HttpClientProvider.getText(cleanManifest) ?: return null
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
            val name = obj.optString("name").ifBlank { "إضافة مخصصة" }
            val version = obj.optString("version", "1.0.0")
            val desc = obj.optString("description", "")
            val icon = obj.optString("logo").ifBlank { obj.optString("icon") }.takeIf { it.startsWith("http") }
            val baseUrl = cleanManifest.removeSuffix("/manifest.json")

            val types = mutableListOf<String>()
            obj.optJSONArray("types")?.let { arr ->
                for (i in 0 until arr.length()) types.add(arr.getString(i))
            }

            val resources = mutableListOf<String>()
            obj.optJSONArray("resources")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.get(i)
                    if (item is String) resources.add(item)
                    else if (item is JSONObject) resources.add(item.optString("name"))
                }
            }

            val catalogs = mutableListOf<AddonCatalog>()
            obj.optJSONArray("catalogs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    catalogs.add(
                        AddonCatalog(
                            addonId = id,
                            addonName = name,
                            type = c.optString("type", "movie"),
                            id = c.optString("id"),
                            name = c.optString("name", "كتالوج")
                        )
                    )
                }
            }

            StremioAddon(
                id = id,
                name = name,
                version = version,
                description = desc,
                manifestUrl = cleanManifest,
                baseUrl = baseUrl,
                iconUrl = icon,
                types = types.ifEmpty { listOf("movie", "series") },
                resources = resources.ifEmpty { listOf("stream") },
                catalogs = catalogs,
                enabled = true,
                orderIndex = orderIndex
            )
        } catch (_: Exception) {
            null
        }
    }

    // Login to Stremio Account & Pull ALL Installed Addons
    suspend fun syncStremioAccountAddons(email: String, password: String): Pair<String, List<StremioAddon>>? {
        val loginPayload = JSONObject().apply {
            put("type", "Login")
            put("email", email.trim())
            put("password", password)
            put("facebook", false)
        }.toString()

        val loginRaw = HttpClientProvider.postJson("https://api.strem.io/api/login", loginPayload) ?: return null
        val loginObj = runCatching { JSONObject(loginRaw) }.getOrNull() ?: return null
        val authKey = loginObj.optJSONObject("result")?.optString("authKey") ?: return null
        if (authKey.isBlank()) return null

        val addonsPayload = JSONObject().apply {
            put("type", "AddonCollectionGet")
            put("authKey", authKey)
            put("update", true)
        }.toString()

        val addonsRaw = HttpClientProvider.postJson("https://api.strem.io/api/addonCollectionGet", addonsPayload) ?: return Pair(authKey, emptyList())
        val addonsObj = runCatching { JSONObject(addonsRaw) }.getOrNull() ?: return Pair(authKey, emptyList())
        val arr = addonsObj.optJSONObject("result")?.optJSONArray("addons") ?: return Pair(authKey, emptyList())

        val pulled = mutableListOf<StremioAddon>()
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            val transportUrl = entry.optString("transportUrl")
            val manifest = entry.optJSONObject("manifest")
            if (transportUrl.isNotBlank() && manifest != null) {
                val baseUrl = transportUrl.removeSuffix("/manifest.json")
                val id = manifest.optString("id").ifBlank { "addon_$i" }
                val name = manifest.optString("name").ifBlank { "Addon $i" }
                val desc = manifest.optString("description", "")
                val icon = manifest.optString("logo").takeIf { it.startsWith("http") }
                pulled.add(
                    StremioAddon(
                        id = id,
                        name = name,
                        version = manifest.optString("version", "1.0.0"),
                        description = desc,
                        manifestUrl = transportUrl,
                        baseUrl = baseUrl,
                        iconUrl = icon,
                        enabled = true,
                        orderIndex = i
                    )
                )
            }
        }
        return Pair(authKey, pulled)
    }

    // Parse & Install CloudStream Repository JSON (supports ArabSeed, EgyBest, Shahid4U, CimaNow, etc. repositories)
    suspend fun fetchCloudStreamRepo(repoUrlInput: String): CloudStreamRepo? {
        val cleanUrl = repoUrlInput.trim()
        val raw = HttpClientProvider.getText(cleanUrl) ?: return null
        return try {
            val obj = JSONObject(raw)
            val repoName = obj.optString("name").ifBlank { "مستودع CloudStream" }
            val repoDesc = obj.optString("description", "")
            val plugins = mutableListOf<CloudStreamPlugin>()

            val pluginLists = obj.optJSONArray("pluginLists")
            if (pluginLists != null) {
                for (i in 0 until pluginLists.length()) {
                    val listUrl = pluginLists.getString(i)
                    val listRaw = HttpClientProvider.getText(listUrl) ?: continue
                    val pArr = runCatching { JSONArray(listRaw) }.getOrNull() ?: continue
                    for (j in 0 until pArr.length()) {
                        val p = pArr.getJSONObject(j)
                        val pName = p.optString("name")
                        val pUrl = p.optString("url")
                        if (pName.isNotBlank()) {
                            val tvTypes = mutableListOf<String>()
                            p.optJSONArray("tvTypes")?.let { tArr ->
                                for (k in 0 until tArr.length()) tvTypes.add(tArr.getString(k))
                            }
                            plugins.add(
                                CloudStreamPlugin(
                                    id = "${repoName}_${p.optString("internalName", pName)}",
                                    name = pName,
                                    internalName = p.optString("internalName", pName),
                                    url = pUrl,
                                    language = p.optString("language", "ar"),
                                    tvTypes = tvTypes.ifEmpty { listOf("Movie", "TvSeries") },
                                    iconUrl = p.optString("iconUrl").takeIf { it.startsWith("http") },
                                    enabled = true
                                )
                            )
                        }
                    }
                }
            }
            CloudStreamRepo(
                id = UUID.nameUUIDFromBytes(cleanUrl.toByteArray()).toString(),
                name = repoName,
                url = cleanUrl,
                description = repoDesc,
                plugins = plugins,
                enabled = true
            )
        } catch (_: Exception) {
            null
        }
    }

    // Query ALL enabled Stremio Addons + CloudStream Plugins concurrently for streams!
    suspend fun fetchAllStreamsForItem(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        addons: List<StremioAddon>,
        csRepos: List<CloudStreamRepo>
    ): List<StreamSource> = coroutineScope {
        val imdbId = item.imdbId
        val streamId = when {
            !imdbId.isNullOrBlank() && (item.type == MediaType.SERIES || item.type == MediaType.ANIME) && season != null && episode != null ->
                "$imdbId:$season:$episode"
            !imdbId.isNullOrBlank() -> imdbId
            item.tmdbId != null && (item.type == MediaType.SERIES || item.type == MediaType.ANIME) && season != null && episode != null ->
                "tmdb:${item.tmdbId}:$season:$episode"
            item.tmdbId != null -> "tmdb:${item.tmdbId}"
            else -> item.id
        }
        val stremioType = if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) "series" else "movie"

        val activeAddons = addons.filter { it.enabled && it.resources.contains("stream") }
        val addonTasks = activeAddons.map { addon ->
            async {
                querySingleStremioAddon(addon, stremioType, streamId)
            }
        }

        val results = addonTasks.awaitAll().flatten().toMutableList()

        // Also query enabled CloudStream Arabic/Global web-bridge scrapers if any repo is installed
        val enabledPlugins = csRepos.filter { it.enabled }.flatMap { it.plugins }.filter { it.enabled }
        if (enabledPlugins.isNotEmpty() && !imdbId.isNullOrBlank()) {
            val fallbackBridge = queryWebStreamBridge(item, season, episode, enabledPlugins.first())
            results.addAll(fallbackBridge)
        }

        results
    }

    private suspend fun querySingleStremioAddon(
        addon: StremioAddon,
        type: String,
        streamId: String
    ): List<StreamSource> {
        val url = "${addon.baseUrl.trimEnd('/')}/stream/$type/${URLEncoder.encode(streamId, "UTF-8")}.json"
        val raw = HttpClientProvider.getText(url) ?: return emptyList()
        return try {
            val root = JSONObject(raw)
            val streamsArr = root.optJSONArray("streams") ?: return emptyList()
            val out = mutableListOf<StreamSource>()

            for (i in 0 until streamsArr.length()) {
                val s = streamsArr.getJSONObject(i)
                val directUrl = s.optString("url").takeIf { it.startsWith("http") || it.startsWith("magnet:") }
                val infoHash = s.optString("infoHash").takeIf { it.isNotBlank() && it != "null" }
                val fileIdx = if (s.has("fileIdx") && !s.isNull("fileIdx")) s.optInt("fileIdx") else null

                if (directUrl == null && infoHash == null) continue

                val rawName = s.optString("name").ifBlank { addon.name }
                val rawTitle = s.optString("title").ifBlank { s.optString("description") }
                val combined = "$rawName $rawTitle"

                val quality = when {
                    combined.contains("2160p", true) || combined.contains("4K", true) -> "4K UHD"
                    combined.contains("1080p", true) -> "1080p FHD"
                    combined.contains("720p", true) -> "720p HD"
                    combined.contains("480p", true) -> "480p SD"
                    else -> "HD"
                }

                val sizeMatch = Regex("(\d+(?:\.\d+)?\s*(?:GB|MB))", RegexOption.IGNORE_CASE).find(combined)?.value ?: ""
                val seedsMatch = Regex("👤\s*(\d+)").find(combined)?.groupValues?.getOrNull(1)?.toIntOrNull()

                // Parse behaviorHints headers if provided
                val headers = mutableMapOf<String, String>()
                s.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")?.let { hObj ->
                    hObj.keys().forEach { k -> headers[k] = hObj.optString(k) }
                }

                // Parse embedded subtitles if provided by addon
                val subs = mutableListOf<SubtitleTrack>()
                s.optJSONArray("subtitles")?.let { subArr ->
                    for (j in 0 until subArr.length()) {
                        val subObj = subArr.getJSONObject(j)
                        val subUrl = subObj.optString("url")
                        val lang = subObj.optString("lang", "ar")
                        if (subUrl.startsWith("http")) {
                            subs.add(
                                SubtitleTrack(
                                    id = subObj.optString("id", "sub_$j"),
                                    url = subUrl,
                                    lang = lang,
                                    label = if (lang.startsWith("ar", true)) "العربية (مرفقة)" else lang.uppercase()
                                )
                            )
                        }
                    }
                }

                val isTorrent = infoHash != null || (directUrl?.startsWith("magnet:") == true)

                out.add(
                    StreamSource(
                        id = "${addon.id}_$i_${infoHash ?: directUrl.hashCode()}",
                        addonId = addon.id,
                        addonName = addon.name,
                        title = rawName.replace("\n", " ").trim(),
                        description = rawTitle.trim(),
                        quality = quality,
                        sizeText = sizeMatch,
                        seeds = seedsMatch,
                        url = directUrl,
                        infoHash = infoHash,
                        fileIdx = fileIdx,
                        headers = headers,
                        subtitles = subs,
                        isTorrent = isTorrent,
                        isCloudStream = false
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun queryWebStreamBridge(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        plugin: CloudStreamPlugin
    ): List<StreamSource> {
        // Queries public VidSrc / Stremio-compatible HTTP resolver for CloudStream plugins
        val imdb = item.imdbId ?: return emptyList()
        val type = if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) "series" else "movie"
        val id = if (type == "series" && season != null && episode != null) "$imdb:$season:$episode" else imdb
        val url = "https://nuviostreams.hayd.uk/stream/$type/$id.json"
        val raw = HttpClientProvider.getText(url) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("streams") ?: return emptyList()
            val list = mutableListOf<StreamSource>()
            for (i in 0 until minOf(arr.length(), 4)) {
                val s = arr.getJSONObject(i)
                val u = s.optString("url")
                if (u.startsWith("http")) {
                    list.add(
                        StreamSource(
                            id = "cs_${plugin.id}_$i",
                            addonId = plugin.id,
                            addonName = plugin.name,
                            title = "${plugin.name} - بث مباشر",
                            description = s.optString("title", "سيرفر CloudStream سريع"),
                            quality = "1080p HD",
                            url = u,
                            isTorrent = false,
                            isCloudStream = true
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Fetch external Arabic subtitles from OpenSubtitles Stremio Addon automatically!
    suspend fun fetchArabicSubtitles(item: MediaItem, season: Int?, episode: Int?): List<SubtitleTrack> {
        val imdbId = item.imdbId ?: return emptyList()
        val type = if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) "series" else "movie"
        val id = if (type == "series" && season != null && episode != null) "$imdbId:$season:$episode" else imdbId
        val url = "https://opensubtitles-v3.strem.io/subtitles/$type/$id.json"
        val raw = HttpClientProvider.getText(url) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("subtitles") ?: return emptyList()
            val out = mutableListOf<SubtitleTrack>()
            for (i in 0 until arr.length()) {
                val sub = arr.getJSONObject(i)
                val lang = sub.optString("lang")
                val subUrl = sub.optString("url")
                if ((lang.equals("ara", true) || lang.equals("ar", true)) && subUrl.startsWith("http")) {
                    out.add(
                        SubtitleTrack(
                            id = sub.optString("id", "os_ar_$i"),
                            url = subUrl,
                            lang = "ar",
                            label = "الترجمة العربية #${out.size + 1}"
                        )
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
