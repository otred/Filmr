package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StremioSyncResult(
    val account: StremioAccountState,
    val addons: List<AddonEntity>,
    val libraryItems: List<LibraryEntity>,
    val progressItems: List<WatchProgressEntity>,
    val watchedItems: List<WatchHistoryEntity>
)

class StremioAddonService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Stremio/4.4.168 (FilmrTV)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JsonParser.parseString(body).asJsonObject
            }
        }.getOrNull()
    }

    private suspend fun postStremioApi(endpoint: String, payload: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.strem.io/api/$endpoint")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JsonParser.parseString(body).asJsonObject
            }
        }.getOrNull()
    }

    fun normalizeManifestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
            .replace("stremio://", "https://")
        return if (trimmed.endsWith("/manifest.json")) {
            trimmed
        } else {
            "${trimmed.trimEnd('/')}/manifest.json"
        }
    }

    suspend fun installAddonFromUrl(
        rawUrl: String,
        isFromAccount: Boolean = false,
        orderIndex: Int = 0
    ): AddonEntity? {
        val manifestUrl = normalizeManifestUrl(rawUrl)
        val obj = getJson(manifestUrl) ?: return null
        val id = obj.get("id")?.asString ?: return null
        val name = obj.get("name")?.asString ?: id
        val description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val version = obj.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "1.0.0"
        val icon = obj.get("logo")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("icon")?.takeIf { !it.isJsonNull }?.asString
        val transportUrl = manifestUrl.removeSuffix("/manifest.json")

        return AddonEntity(
            id = "stremio_$id",
            name = name,
            description = description,
            manifestUrl = manifestUrl,
            transportUrl = transportUrl,
            version = version,
            iconUrl = icon,
            sourceType = "stremio",
            isFromStremioAccount = isFromAccount,
            enabled = true,
            orderIndex = orderIndex,
            rawManifestJson = obj.toString()
        )
    }

    /**
     * Fast Stremio Account Login & Full Sync:
     * Pulls installed Addons + Library + Continue Watching + Watched History in parallel!
     */
    suspend fun loginAndSyncStremioAccount(email: String, password: String): Result<StremioSyncResult> {
        val loginPayload = JsonObject().apply {
            addProperty("type", "Login")
            addProperty("email", email.trim())
            addProperty("password", password)
            addProperty("facebook", false)
        }
        val loginResp = postStremioApi("login", loginPayload)
            ?: return Result.failure(Exception("فشل الاتصال بخوادم Stremio"))

        if (loginResp.has("error")) {
            val errMsg = loginResp.getAsJsonObject("error")?.get("message")?.asString
                ?: "بيانات الدخول غير صحيحة"
            return Result.failure(Exception(errMsg))
        }

        val resultObj = loginResp.getAsJsonObject("result")
            ?: return Result.failure(Exception("استجابة غير صالحة من Stremio"))
        val authKey = resultObj.get("authKey")?.asString
            ?: return Result.failure(Exception("تعذر الحصول على مفتاح المصادقة"))
        val userObj = resultObj.getAsJsonObject("user")
        val userId = userObj?.get("_id")?.asString.orEmpty()
        val userEmail = userObj?.get("email")?.asString ?: email.trim()

        return syncWithAuthKey(authKey, userEmail, userId)
    }

    suspend fun syncWithAuthKey(
        authKey: String,
        email: String,
        userId: String = ""
    ): Result<StremioSyncResult> = coroutineScope {
        val addonsDeferred = async {
            val payload = JsonObject().apply {
                addProperty("type", "AddonCollectionGet")
                addProperty("authKey", authKey)
                addProperty("update", true)
            }
            postStremioApi("addonCollectionGet", payload)
        }

        val libraryDeferred = async {
            val payload = JsonObject().apply {
                addProperty("type", "DatastoreGet")
                addProperty("authKey", authKey)
                addProperty("collection", "libraryItem")
                addProperty("all", true)
            }
            postStremioApi("datastoreGet", payload)
        }

        val addonsResp = addonsDeferred.await()
        val libraryResp = libraryDeferred.await()

        if (addonsResp == null && libraryResp == null) {
            return@coroutineScope Result.failure(Exception("تعذر مزامنة بيانات حساب Stremio"))
        }

        // 1. Parse Installed Addons from Stremio Account
        val syncedAddons = mutableListOf<AddonEntity>()
        val addonsArray = addonsResp?.getAsJsonObject("result")?.getAsJsonArray("addons")
        addonsArray?.forEachIndexed { idx, el ->
            val item = el.asJsonObject
            val transportUrl = item.get("transportUrl")?.asString ?: return@forEachIndexed
            val manifest = item.getAsJsonObject("manifest") ?: return@forEachIndexed
            val id = manifest.get("id")?.asString ?: return@forEachIndexed
            val name = manifest.get("name")?.asString ?: id
            val desc = manifest.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val version = manifest.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "1.0.0"
            val logo = manifest.get("logo")?.takeIf { !it.isJsonNull }?.asString
            val normManifest = normalizeManifestUrl(transportUrl)

            syncedAddons.add(
                AddonEntity(
                    id = "stremio_$id",
                    name = name,
                    description = desc,
                    manifestUrl = normManifest,
                    transportUrl = normManifest.removeSuffix("/manifest.json"),
                    version = version,
                    iconUrl = logo,
                    sourceType = "stremio",
                    isFromStremioAccount = true,
                    enabled = true,
                    orderIndex = idx,
                    rawManifestJson = manifest.toString()
                )
            )
        }

        // 2. Parse Library, Watch Progress (Continue Watching), and Watched Items
        val syncedLibrary = mutableListOf<LibraryEntity>()
        val syncedProgress = mutableListOf<WatchProgressEntity>()
        val syncedWatched = mutableListOf<WatchHistoryEntity>()

        val libArray = libraryResp?.getAsJsonArray("result")
        libArray?.forEach { el ->
            val obj = el.asJsonObject
            val id = obj.get("_id")?.asString ?: return@forEach
            val type = obj.get("type")?.asString ?: "movie"
            if (type != "movie" && type != "series") return@forEach
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            val poster = obj.get("poster")?.takeIf { !it.isJsonNull }?.asString
            val background = obj.get("background")?.takeIf { !it.isJsonNull }?.asString
            val year = obj.get("year")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val removed = obj.get("removed")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            val temp = obj.get("temp")?.takeIf { !it.isJsonNull }?.asBoolean ?: false

            if (!removed && !temp) {
                syncedLibrary.add(
                    LibraryEntity(
                        mediaId = id,
                        imdbId = id.takeIf { it.startsWith("tt") },
                        mediaType = type,
                        titleAr = name,
                        titleEn = name,
                        posterUrl = poster,
                        backdropUrl = background,
                        year = year,
                        syncedFromStremio = true
                    )
                )
            }

            val stateObj = obj.getAsJsonObject("state")
            if (stateObj != null) {
                val timeOffset = stateObj.get("timeOffset")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val duration = stateObj.get("duration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val timesWatched = stateObj.get("timesWatched")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val videoId = stateObj.get("video_id")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

                if (timesWatched > 0) {
                    syncedWatched.add(
                        WatchHistoryEntity(
                            mediaId = id,
                            mediaType = type,
                            title = name,
                            posterUrl = poster
                        )
                    )
                }

                if (timeOffset > 30_000L && duration > 0L && (timeOffset.toFloat() / duration.toFloat()) < 0.92f) {
                    var season = 0
                    var episode = 0
                    if (videoId.contains(":")) {
                        val parts = videoId.split(":")
                        if (parts.size >= 3) {
                            season = parts[1].toIntOrNull() ?: 0
                            episode = parts[2].toIntOrNull() ?: 0
                        }
                    }
                    val progKey = if (season > 0 && episode > 0) "$id:$season:$episode" else id
                    syncedProgress.add(
                        WatchProgressEntity(
                            key = progKey,
                            mediaId = id,
                            imdbId = id.takeIf { it.startsWith("tt") },
                            mediaType = type,
                            title = name,
                            posterUrl = poster,
                            backdropUrl = background,
                            season = season,
                            episode = episode,
                            positionMs = timeOffset,
                            durationMs = duration
                        )
                    )
                }
            }
        }

        val accountState = StremioAccountState(
            isLoggedIn = true,
            email = email,
            authKey = authKey,
            userId = userId,
            lastSyncTimestamp = System.currentTimeMillis(),
            syncedAddonsCount = syncedAddons.size,
            syncedLibraryCount = syncedLibrary.size,
            syncedHistoryCount = syncedProgress.size + syncedWatched.size
        )

        Result.success(
            StremioSyncResult(
                account = accountState,
                addons = syncedAddons,
                libraryItems = syncedLibrary,
                progressItems = syncedProgress,
                watchedItems = syncedWatched
            )
        )
    }

    suspend fun fetchAddonCatalogs(addon: AddonEntity): List<AddonCatalogRow> {
        val manifestObj = runCatching {
            if (addon.rawManifestJson.isNotBlank()) JsonParser.parseString(addon.rawManifestJson).asJsonObject
            else getJson(addon.manifestUrl)
        }.getOrNull() ?: return emptyList()

        val catalogs = manifestObj.getAsJsonArray("catalogs") ?: return emptyList()
        val rows = mutableListOf<AddonCatalogRow>()

        for (el in catalogs.take(3)) {
            val cat = el.asJsonObject
            val type = cat.get("type")?.asString ?: continue
            val catId = cat.get("id")?.asString ?: continue
            val catName = cat.get("name")?.takeIf { !it.isJsonNull }?.asString ?: addon.name

            // Skip search-only catalogs
            val extra = cat.getAsJsonArray("extra")
            val requiresSearch = extra?.any {
                val eo = it.asJsonObject
                eo.get("name")?.asString == "search" && (eo.get("isRequired")?.asBoolean == true)
            } ?: false
            if (requiresSearch) continue

            val url = "${addon.transportUrl}/catalog/$type/$catId.json"
            val catResp = getJson(url) ?: continue
            val metas = catResp.getAsJsonArray("metas") ?: continue
            val items = metas.take(20).mapNotNull { parseStremioMeta(it.asJsonObject, addon.name) }
            if (items.isNotEmpty()) {
                rows.add(
                    AddonCatalogRow(
                        id = "${addon.id}_${type}_$catId",
                        title = "$catName • ${addon.name}",
                        addonName = addon.name,
                        addonType = "stremio",
                        items = items
                    )
                )
            }
        }
        return rows
    }

    suspend fun searchInAddon(addon: AddonEntity, query: String): List<MediaItem> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val results = mutableListOf<MediaItem>()
        for (type in listOf("movie", "series")) {
            val url = "${addon.transportUrl}/catalog/$type/top/search=$encoded.json"
            val resp = getJson(url) ?: continue
            val metas = resp.getAsJsonArray("metas") ?: continue
            results.addAll(metas.take(15).mapNotNull { parseStremioMeta(it.asJsonObject, addon.name) })
        }
        return results
    }

    suspend fun fetchStreamsForAddon(
        addon: AddonEntity,
        mediaType: String,
        streamId: String
    ): List<UnifiedStream> {
        val stremioType = if (mediaType == "tv") "series" else mediaType
        val url = "${addon.transportUrl}/stream/$stremioType/$streamId.json"
        val resp = getJson(url) ?: return emptyList()
        val streamsArr = resp.getAsJsonArray("streams") ?: return emptyList()

        return streamsArr.mapIndexedNotNull { idx, el ->
            val so = el.asJsonObject
            val rawUrl = so.get("url")?.takeIf { !it.isJsonNull }?.asString
            val infoHash = so.get("infoHash")?.takeIf { !it.isJsonNull }?.asString
            val fileIdx = so.get("fileIdx")?.takeIf { !it.isJsonNull }?.asInt
            if (rawUrl.isNullOrBlank() && infoHash.isNullOrBlank()) return@mapIndexedNotNull null

            val title = so.get("title")?.takeIf { !it.isJsonNull }?.asString
                ?: so.get("description")?.takeIf { !it.isJsonNull }?.asString
                ?: so.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?: "${addon.name} #${idx + 1}"
            val name = so.get("name")?.takeIf { !it.isJsonNull }?.asString ?: addon.name

            val isTorrent = !infoHash.isNullOrBlank() || (rawUrl?.startsWith("magnet:") == true)
            val resolvedUrl = when {
                !rawUrl.isNullOrBlank() -> rawUrl
                !infoHash.isNullOrBlank() -> "magnet:?xt=urn:btih:$infoHash&dn=${URLEncoder.encode(title.take(40), "UTF-8")}"
                else -> null
            }

            val fullText = "$name $title"
            val quality = extractQuality(fullText)
            val sizeText = extractSize(fullText)
            val seeders = extractSeeders(fullText)

            val subs = mutableListOf<SubtitleTrack>()
            so.getAsJsonArray("subtitles")?.forEachIndexed { sIdx, sEl ->
                val subObj = sEl.asJsonObject
                val sUrl = subObj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@forEachIndexed
                val sLang = subObj.get("lang")?.takeIf { !it.isJsonNull }?.asString ?: "und"
                subs.add(
                    SubtitleTrack(
                        id = "${addon.id}_sub_${idx}_$sIdx",
                        url = sUrl,
                        lang = sLang,
                        label = "$sLang (${addon.name})",
                        addonName = addon.name
                    )
                )
            }

            UnifiedStream(
                id = "${addon.id}_stream_$idx",
                title = name,
                description = title,
                url = resolvedUrl,
                infoHash = infoHash,
                fileIdx = fileIdx,
                quality = quality,
                sizeText = sizeText,
                seeders = seeders,
                isTorrent = isTorrent,
                sourceEngine = if (isTorrent) "torrent" else "stremio",
                addonName = addon.name,
                addonUrl = addon.transportUrl,
                subtitles = subs
            )
        }
    }

    suspend fun fetchSubtitlesForAddon(
        addon: AddonEntity,
        mediaType: String,
        streamId: String
    ): List<SubtitleTrack> {
        val stremioType = if (mediaType == "tv") "series" else mediaType
        val url = "${addon.transportUrl}/subtitles/$stremioType/$streamId.json"
        val resp = getJson(url) ?: return emptyList()
        val subsArr = resp.getAsJsonArray("subtitles") ?: return emptyList()
        return subsArr.mapIndexedNotNull { idx, el ->
            val so = el.asJsonObject
            val sUrl = so.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@mapIndexedNotNull null
            val lang = so.get("lang")?.takeIf { !it.isJsonNull }?.asString ?: "ar"
            SubtitleTrack(
                id = "${addon.id}_extsub_$idx",
                url = sUrl,
                lang = lang,
                label = "${lang.uppercase()} • ${addon.name}",
                addonName = addon.name
            )
        }
    }

    private fun parseStremioMeta(obj: JsonObject, addonName: String): MediaItem? {
        val id = obj.get("id")?.asString ?: return null
        val type = obj.get("type")?.asString ?: "movie"
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val poster = obj.get("poster")?.takeIf { !it.isJsonNull }?.asString
        val background = obj.get("background")?.takeIf { !it.isJsonNull }?.asString
        val logo = obj.get("logo")?.takeIf { !it.isJsonNull }?.asString
        val desc = obj.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val releaseInfo = obj.get("releaseInfo")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val imdbRating = obj.get("imdbRating")?.takeIf { !it.isJsonNull }?.asString

        return MediaItem(
            id = id,
            imdbId = id.takeIf { it.startsWith("tt") },
            type = type,
            titleAr = name,
            titleEn = name,
            posterUrl = poster,
            backdropUrl = background,
            logoUrl = logo,
            overview = desc,
            year = releaseInfo.take(4),
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            sourceAddonName = addonName
        )
    }

    private fun extractQuality(text: String): String {
        val up = text.uppercase()
        return when {
            up.contains("2160P") || up.contains("4K") || up.contains("UHD") -> "4K UHD"
            up.contains("1080P") -> "1080p"
            up.contains("720P") -> "720p"
            up.contains("480P") -> "480p"
            up.contains("CAM") || up.contains("TS") -> "CAM"
            else -> "HD"
        }
    }

    private fun extractSize(text: String): String {
        val match = Regex("(\d+(?:\.\d+)?\s*(?:GB|MB|GiB|MiB))", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1).orEmpty()
    }

    private fun extractSeeders(text: String): Int {
        val match = Regex("(?:👤|S:|Seeds?:)\s*(\d+)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
