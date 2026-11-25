package com.filmr.tv.engine
import com.filmr.tv.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object TmdbArtworkEngine {
    private val translationCache = ConcurrentHashMap<String, String>()
    private const val IMG_W500 = "https://image.tmdb.org/t/p/w500"
    private const val IMG_W780 = "https://image.tmdb.org/t/p/w780"
    private fun httpGet(u: String): String = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000; setRequestProperty("User-Agent", "FilmrTV/2.0") }.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    suspend fun translateToArabic(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.any { it in '\u0600'..'\u06FF' }) return@withContext text
        translationCache[text]?.let { return@withContext it }
        runCatching {
            val q = URLEncoder.encode(text.take(900), "UTF-8")
            val sentences = JsonParser.parseString(httpGet("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ar&dt=t&q=$q")).asJsonArray[0].asJsonArray
            val sb = StringBuilder()
            for (el in sentences) { val p = el.asJsonArray[0]; if (!p.isJsonNull) sb.append(p.asString) }
            sb.toString().trim().also { if (it.isNotBlank()) translationCache[text] = it }.ifBlank { text }
        }.getOrDefault(text)
    }

    suspend fun fetchTrending(type: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching { parseMediaList(httpGet("https://api.themoviedb.org/3/trending/${if (type == "series") "tv" else "movie"}/week?api_key=$apiKey&language=ar-SA"), type) }.getOrDefault(emptyList())
    }

    suspend fun fetchByNetwork(networkId: Int, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching { parseMediaList(httpGet("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=ar-SA&with_networks=$networkId&sort_by=popularity.desc"), "series") }.getOrDefault(emptyList())
    }
    suspend fun searchMulti(query: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val results = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar-SA&query=$q")).asJsonObject.getAsJsonArray("results") ?: return@runCatching emptyList()
            results.mapNotNull { el ->
                val obj = el.asJsonObject
                val mt = obj.get("media_type")?.asString ?: return@mapNotNull null
                if (mt != "movie" && mt != "tv") return@mapNotNull null
                parseSingleMedia(obj, if (mt == "tv") "series" else "movie")
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchDetails(item: MediaItem, apiKey: String, mdbKey: String): MediaItem = withContext(Dispatchers.IO) {
        runCatching {
            val tmdbType = if (item.type == "series") "tv" else "movie"
            var tmdbId = item.tmdbId
            if (tmdbId == 0 && item.imdbId.startsWith("tt")) {
                val findObj = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/find/${item.imdbId}?api_key=$apiKey&external_source=imdb_id&language=ar-SA")).asJsonObject
                val arr = findObj.getAsJsonArray(if (tmdbType == "tv") "tv_results" else "movie_results")
                if (arr != null && arr.size() > 0) tmdbId = arr[0].asJsonObject.get("id")?.asInt ?: 0
            }
            if (tmdbId == 0) return@runCatching item
            val arObj = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=ar-SA&append_to_response=credits,external_ids")).asJsonObject
            val titleAr = (arObj.get("title") ?: arObj.get("name"))?.asString ?: item.titleAr
            val titleEn = (arObj.get("original_title") ?: arObj.get("original_name"))?.asString ?: item.titleEn
            var overview = arObj.get("overview")?.asString ?: ""
            if (overview.isBlank()) {
                val enOver = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=en-US")).asJsonObject.get("overview")?.asString ?: ""
                overview = if (enOver.isNotBlank()) translateToArabic(enOver) else ""
            } else if (!overview.any { it in '\u0600'..'\u06FF' }) overview = translateToArabic(overview)
            val imdbId = arObj.getAsJsonObject("external_ids")?.get("imdb_id")?.takeIf { !it.isJsonNull }?.asString ?: item.imdbId
            val poster = arObj.get("poster_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W500$it" } ?: item.posterUrl
            val backdrop = arObj.get("backdrop_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W780$it" } ?: item.backdropUrl
            val rating = arObj.get("vote_average")?.asDouble ?: item.rating
            val seasonsCount = arObj.get("number_of_seasons")?.asInt ?: 1
            val relDate = (arObj.get("release_date") ?: arObj.get("first_air_date"))?.takeIf { !it.isJsonNull }?.asString ?: item.releaseDate
            val genres = arObj.getAsJsonArray("genres")?.mapNotNull { it.asJsonObject.get("name")?.asString } ?: emptyList()
            val networks = arObj.getAsJsonArray("networks") ?: arObj.getAsJsonArray("production_companies")
            val firstNet = if (networks != null && networks.size() > 0) networks[0].asJsonObject else null
            val castList = arObj.getAsJsonObject("credits")?.getAsJsonArray("cast")?.take(15)?.mapNotNull { el ->
                val c = el.asJsonObject
                val cId = c.get("id")?.asInt ?: return@mapNotNull null
                val cName = c.get("name")?.asString ?: return@mapNotNull null
                CastMember(cId, cName, c.get("character")?.asString ?: "", c.get("profile_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W500$it" } ?: "")
            } ?: emptyList()
            var mdbScore = ""
            if (mdbKey.isNotBlank() && imdbId.startsWith("tt")) {
                runCatching {
                    val sc = JsonParser.parseString(httpGet("https://mdblist.com/api/?apikey=$mdbKey&i=$imdbId")).asJsonObject.get("score")?.asInt
                    if (sc != null && sc > 0) mdbScore = "$sc%"
                }
            }
            item.copy(id = imdbId.ifBlank { "tmdb:$tmdbId" }, imdbId = imdbId, tmdbId = tmdbId, titleAr = titleAr, titleEn = titleEn, posterUrl = poster, backdropUrl = backdrop, overviewAr = overview, rating = rating, mdbRating = mdbScore, year = relDate.take(4), releaseDate = relDate, genres = genres, network = firstNet?.get("name")?.asString ?: "", networkId = firstNet?.get("id")?.asInt ?: 0, seasonsCount = seasonsCount, cast = castList)
        }.getOrDefault(item)
    }

    suspend fun fetchSeasonEpisodes(tmdbId: Int, season: Int, apiKey: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        runCatching {
            val eps = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$apiKey&language=ar-SA")).asJsonObject.getAsJsonArray("episodes") ?: return@runCatching emptyList()
            eps.map { el ->
                val e = el.asJsonObject
                val epNum = e.get("episode_number")?.asInt ?: 1
                EpisodeItem(season, epNum, e.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "الحلقة $epNum", e.get("overview")?.takeIf { !it.isJsonNull }?.asString ?: "", e.get("air_date")?.takeIf { !it.isJsonNull }?.asString ?: "", e.get("still_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W500$it" } ?: "")
            }
        }.getOrDefault(emptyList())
    }
    suspend fun fetchArtworkGallery(tmdbId: Int, type: String, apiKey: String): ArtworkGallery = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext ArtworkGallery()
        runCatching {
            val t = if (type == "series") "tv" else "movie"
            val obj = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/$t/$tmdbId/images?api_key=$apiKey&include_image_language=ar,en,null")).asJsonObject
            val posters = obj.getAsJsonArray("posters")?.take(24)?.mapNotNull { it.asJsonObject.get("file_path")?.takeIf { p -> !p.isJsonNull }?.asString?.let { p -> "$IMG_W500$p" } } ?: emptyList()
            val backdrops = obj.getAsJsonArray("backdrops")?.take(24)?.mapNotNull { it.asJsonObject.get("file_path")?.takeIf { p -> !p.isJsonNull }?.asString?.let { p -> "$IMG_W780$p" } } ?: emptyList()
            ArtworkGallery(posters, backdrops)
        }.getOrDefault(ArtworkGallery())
    }

    suspend fun fetchByPerson(personId: Int, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cast = JsonParser.parseString(httpGet("https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=ar-SA")).asJsonObject.getAsJsonArray("cast") ?: return@runCatching emptyList()
            cast.take(30).mapNotNull { el -> val c = el.asJsonObject; parseSingleMedia(c, if (c.get("media_type")?.asString == "tv") "series" else "movie") }.distinctBy { it.tmdbId }
        }.getOrDefault(emptyList())
    }

    private fun parseMediaList(raw: String, type: String): List<MediaItem> = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("results")?.mapNotNull { parseSingleMedia(it.asJsonObject, type) } ?: emptyList()

    private fun parseSingleMedia(obj: JsonObject, type: String): MediaItem? {
        val tmdbId = obj.get("id")?.asInt ?: return null
        val titleAr = (obj.get("title") ?: obj.get("name"))?.takeIf { !it.isJsonNull }?.asString ?: ""
        val titleEn = (obj.get("original_title") ?: obj.get("original_name"))?.takeIf { !it.isJsonNull }?.asString ?: titleAr
        val relDate = (obj.get("release_date") ?: obj.get("first_air_date"))?.takeIf { !it.isJsonNull }?.asString ?: ""
        return MediaItem("tmdb:$tmdbId", tmdbId = tmdbId, type = type, titleAr = titleAr, titleEn = titleEn, posterUrl = obj.get("poster_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W500$it" } ?: "", backdropUrl = obj.get("backdrop_path")?.takeIf { !it.isJsonNull }?.asString?.let { "$IMG_W780$it" } ?: "", overviewAr = obj.get("overview")?.takeIf { !it.isJsonNull }?.asString ?: "", rating = obj.get("vote_average")?.asDouble ?: 0.0, year = relDate.take(4), releaseDate = relDate)
    }
}
