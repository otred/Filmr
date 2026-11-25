package com.filmr.tv.engine

import com.filmr.tv.data.AddonEntity
import com.filmr.tv.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class StremioEngine {
    private val gson = Gson()
    private val client = HttpClient(Android) {
        engine {
            connectTimeout = 15_000
            socketTimeout = 20_000
        }
    }

    suspend fun loginStremio(email: String, password: String): Result<StremioUserAccount> = runCatching {
        val payload = mapOf(
            "type" to "Login",
            "email" to email.trim(),
            "password" to password
        )
        val response = client.post("https://api.strem.io/api/login") {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }.bodyAsText()

        val root = gson.fromJson(response, JsonObject::class.java)
        if (root.has("error")) {
            val err = root.getAsJsonObject("error")?.get("message")?.asString ?: "فشل تسجيل الدخول"
            throw IllegalStateException(err)
        }
        val result = root.getAsJsonObject("result")
        val authKey = result.get("authKey").asString
        val userObj = result.getAsJsonObject("user")
        StremioUserAccount(
            authKey = authKey,
            email = userObj?.get("email")?.asString ?: email,
            fullName = userObj?.get("fullname")?.asString ?: email.substringBefore("@"),
            avatarUrl = userObj?.get("avatar")?.asString
        )
    }

    suspend fun fetchUserAddonsFromAccount(authKey: String): Result<List<AddonEntity>> = runCatching {
        val payload = mapOf(
            "type" to "AddonCollectionGet",
            "authKey" to authKey,
            "update" to true
        )
        val response = client.post("https://api.strem.io/api/addonCollectionGet") {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }.bodyAsText()

        val root = gson.fromJson(response, JsonObject::class.java)
        val addonsArray = root.getAsJsonObject("result")?.getAsJsonArray("addons") ?: return@runCatching emptyList()

        addonsArray.mapNotNull { el ->
            val obj = el.asJsonObject
            val transportUrl = obj.get("transportUrl")?.asString ?: return@mapNotNull null
            val manifest = obj.getAsJsonObject("manifest") ?: return@mapNotNull null
            AddonEntity(
                url = transportUrl,
                name = manifest.get("name")?.asString ?: "Stremio Addon",
                description = manifest.get("description")?.asString ?: "",
                iconUrl = manifest.get("logo")?.asString,
                type = "STREMIO",
                isEnabled = true
            )
        }
    }

    suspend fun fetchManifest(manifestUrl: String): Result<AddonEntity> = runCatching {
        val cleanUrl = manifestUrl.trim().replace("stremio://", "https://")
        val text = client.get(cleanUrl).bodyAsText()
        val manifest = gson.fromJson(text, JsonObject::class.java)
        AddonEntity(
            url = cleanUrl,
            name = manifest.get("name")?.asString ?: "إضافة ستريمو",
            description = manifest.get("description")?.asString ?: "",
            iconUrl = manifest.get("logo")?.asString,
            type = "STREMIO",
            isEnabled = true
        )
    }

    suspend fun fetchCatalog(manifestUrl: String, type: String = "movie", catalogId: String = "top"): List<MediaItem> = runCatching {
        val baseUrl = manifestUrl.removeSuffix("/manifest.json")
        val url = "$baseUrl/catalog/$type/$catalogId.json"
        val text = client.get(url).bodyAsText()
        val metas = gson.fromJson(text, JsonObject::class.java).getAsJsonArray("metas") ?: return emptyList()

        metas.mapNotNull { item ->
            val m = item.asJsonObject
            val id = m.get("id")?.asString ?: return@mapNotNull null
            MediaItem(
                id = id,
                imdbId = id.takeIf { it.startsWith("tt") },
                type = m.get("type")?.asString ?: type,
                title = m.get("name")?.asString ?: "",
                overview = m.get("description")?.asString ?: "",
                posterUrl = m.get("poster")?.asString ?: "",
                backdropUrl = m.get("background")?.asString ?: m.get("poster")?.asString ?: "",
                logoUrl = m.get("logo")?.asString,
                year = m.get("releaseInfo")?.asString ?: "",
                rating = m.get("imdbRating")?.asString ?: "8.0"
            )
        }
    }.getOrDefault(emptyList())

    suspend fun fetchMeta(manifestUrl: String, type: String, id: String): MediaItem? = runCatching {
        val baseUrl = manifestUrl.removeSuffix("/manifest.json")
        val text = client.get("$baseUrl/meta/$type/$id.json").bodyAsText()
        val m = gson.fromJson(text, JsonObject::class.java).getAsJsonObject("meta") ?: return null

        val videos = m.getAsJsonArray("videos")
        val seasonsMap = mutableMapOf<Int, MutableList<EpisodeData>>()
        videos?.forEach { vEl ->
            val v = vEl.asJsonObject
            val s = v.get("season")?.asInt ?: 1
            val ep = v.get("episode")?.asInt ?: v.get("number")?.asInt ?: 1
            if (s > 0) {
                seasonsMap.getOrPut(s) { mutableListOf() }.add(
                    EpisodeData(
                        id = v.get("id")?.asString ?: "$id:$s:$ep",
                        season = s,
                        episode = ep,
                        title = v.get("title")?.asString ?: v.get("name")?.asString ?: "الحلقة $ep",
                        overview = v.get("overview")?.asString ?: "",
                        thumbnail = v.get("thumbnail")?.asString ?: ""
                    )
                )
            }
        }

        val seasonsList = seasonsMap.toSortedMap().map { (sNum, eps) ->
            SeasonData(
                seasonNumber = sNum,
                title = "الموسم $sNum",
                episodes = eps.sortedBy { it.episode }
            )
        }

        MediaItem(
            id = id,
            imdbId = id.takeIf { it.startsWith("tt") },
            type = type,
            title = m.get("name")?.asString ?: "",
            overview = m.get("description")?.asString ?: "",
            posterUrl = m.get("poster")?.asString ?: "",
            backdropUrl = m.get("background")?.asString ?: m.get("poster")?.asString ?: "",
            logoUrl = m.get("logo")?.asString,
            year = m.get("releaseInfo")?.asString ?: "",
            rating = m.get("imdbRating")?.asString ?: "8.4",
            seasons = seasonsList
        )
    }.getOrNull()

    suspend fun fetchStreams(addon: AddonEntity, type: String, videoId: String): List<UnifiedStream> = runCatching {
        val baseUrl = addon.url.removeSuffix("/manifest.json")
        val text = client.get("$baseUrl/stream/$type/$videoId.json").bodyAsText()
        val streams = gson.fromJson(text, JsonObject::class.java).getAsJsonArray("streams") ?: return emptyList()

        streams.mapIndexedNotNull { index, sEl ->
            val s = sEl.asJsonObject
            val streamUrl = s.get("url")?.asString ?: return@mapIndexedNotNull null
            val title = s.get("title")?.asString ?: s.get("name")?.asString ?: "بث مباشر"
            val quality = when {
                title.contains("2160") || title.contains("4K", ignoreCase = true) -> 2160
                title.contains("1080") -> 1080
                title.contains("720") -> 720
                else -> 480
            }
            val subs = s.getAsJsonArray("subtitles")?.mapIndexedNotNull { sIdx, subEl ->
                val subObj = subEl.asJsonObject
                val subUrl = subObj.get("url")?.asString ?: return@mapIndexedNotNull null
                val lang = subObj.get("lang")?.asString ?: "ar"
                SubtitleTrack(
                    id = "stremio_sub_${index}_$sIdx",
                    language = lang,
                    label = if (lang.startsWith("ar")) "العربية ($lang)" else lang,
                    url = subUrl
                )
            } ?: emptyList()

            UnifiedStream(
                id = "${addon.name}_$index",
                title = title.replace("\n", " • "),
                sourceName = addon.name,
                sourceType = AddonSourceType.STREMIO,
                url = streamUrl,
                qualityScore = quality,
                subtitles = subs
            )
        }
    }.getOrDefault(emptyList())

    suspend fun fetchSubtitles(addon: AddonEntity, type: String, videoId: String): List<SubtitleTrack> = runCatching {
        val baseUrl = addon.url.removeSuffix("/manifest.json")
        val text = client.get("$baseUrl/subtitles/$type/$videoId.json").bodyAsText()
        val subs = gson.fromJson(text, JsonObject::class.java).getAsJsonArray("subtitles") ?: return emptyList()

        subs.mapIndexedNotNull { idx, el ->
            val obj = el.asJsonObject
            val subUrl = obj.get("url")?.asString ?: return@mapIndexedNotNull null
            val lang = obj.get("lang")?.asString ?: "unknown"
            SubtitleTrack(
                id = "${addon.name}_sub_$idx",
                language = lang,
                label = "${addon.name} - $lang",
                url = subUrl
            )
        }
    }.getOrDefault(emptyList())
}
