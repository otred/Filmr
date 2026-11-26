package com.filmr.tv.engine

import com.filmr.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TmdbArtworkEngine {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val imgBase = "https://image.tmdb.org/t/p/w500"
    private val backdropBase = "https://image.tmdb.org/t/p/w1280"

    private fun getJson(url: String): JSONObject? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return null
            res.body?.string()?.let { JSONObject(it) }
        }
    } catch (_: Exception) { null }

    private fun parseMedia(obj: JSONObject, forcedType: String? = null): MediaItem {
        val mediaType = forcedType ?: obj.optString("media_type", if (obj.has("first_air_date")) "series" else "movie")
        val isTv = mediaType == "tv" || mediaType == "series"
        val tmdbId = obj.optInt("id")
        val title = if (isTv) obj.optString("name") else obj.optString("title")
        val origTitle = if (isTv) obj.optString("original_name") else obj.optString("original_title")
        val date = if (isTv) obj.optString("first_air_date") else obj.optString("release_date")
        val poster = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$imgBase$it" } ?: ""
        val backdrop = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$backdropBase$it" } ?: ""
        return MediaItem(
            id = "tmdb_${if (isTv) "tv" else "movie"}_$tmdbId",
            tmdbId = tmdbId,
            type = if (isTv) "series" else "movie",
            titleAr = title.ifBlank { origTitle },
            titleEn = origTitle.ifBlank { title },
            posterUrl = poster,
            backdropUrl = backdrop,
            overviewAr = obj.optString("overview"),
            rating = obj.optDouble("vote_average", 0.0),
            year = date.take(4),
            released = date
        )
    }

    suspend fun getTrending(type: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbType = if (type == "series") "tv" else "movie"
        val json = getJson("https://api.themoviedb.org/3/trending/$tmdbType/week?api_key=$apiKey&language=ar") ?: return@withContext emptyList()
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until minOf(arr.length(), 18)).map { parseMedia(arr.getJSONObject(it), tmdbType) }
    }

    suspend fun getByNetwork(networkId: Int, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = getJson("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=ar&with_networks=$networkId&sort_by=popularity.desc") ?: return@withContext emptyList()
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until minOf(arr.length(), 16)).map { parseMedia(arr.getJSONObject(it), "tv") }
    }

    suspend fun searchArabic(query: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = getJson("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar&query=$q&include_adult=false") ?: return@withContext emptyList()
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        val out = mutableListOf<MediaItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val mt = obj.optString("media_type")
            if (mt == "movie" || mt == "tv") out.add(parseMedia(obj, mt))
        }
        out
    }

    suspend fun enrichDetails(item: MediaItem, settings: AppSettings): MediaItem = withContext(Dispatchers.IO) {
        val key = settings.tmdbApiKey
        val isTv = item.type == "series"
        val tmdbType = if (isTv) "tv" else "movie"
        var tmdbId = item.tmdbId
        var imdbId = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }

        if (tmdbId == 0 && imdbId.isNotBlank()) {
            val find = getJson("https://api.themoviedb.org/3/find/$imdbId?api_key=$key&external_source=imdb_id")
            val resArr = find?.optJSONArray(if (isTv) "tv_results" else "movie_results")
            if (resArr != null && resArr.length() > 0) tmdbId = resArr.getJSONObject(0).optInt("id")
        }
        if (tmdbId == 0) {
            val q = URLEncoder.encode(item.titleEn.ifBlank { item.titleAr }, "UTF-8")
            val s = getJson("https://api.themoviedb.org/3/search/$tmdbType?api_key=$key&query=$q")
            val arr = s?.optJSONArray("results")
            if (arr != null && arr.length() > 0) tmdbId = arr.getJSONObject(0).optInt("id")
        }
        if (tmdbId == 0) return@withContext item

        val d = getJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$key&language=ar&append_to_response=external_ids,credits,images&include_image_language=en,ar,null") ?: return@withContext item
        val extImdb = d.optJSONObject("external_ids")?.optString("imdb_id").orEmpty()
        if (extImdb.startsWith("tt")) imdbId = extImdb

        val genres = mutableListOf<String>()
        d.optJSONArray("genres")?.let { gArr -> for (i in 0 until gArr.length()) genres.add(gArr.getJSONObject(i).optString("name")) }

        val cast = mutableListOf<CastMember>()
        d.optJSONObject("credits")?.optJSONArray("cast")?.let { cArr ->
            for (i in 0 until minOf(cArr.length(), 12)) {
                val c = cArr.getJSONObject(i)
                val p = c.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$imgBase$it" } ?: ""
                cast.add(CastMember(c.optInt("id"), c.optString("name"), c.optString("character"), p))
            }
        }

        val logos = d.optJSONObject("images")?.optJSONArray("logos")
        val logoUrl = if (logos != null && logos.length() > 0) "$imgBase${logos.getJSONObject(0).optString("file_path")}" else item.logoUrl

        val networks = d.optJSONArray("networks")
        val netName = if (networks != null && networks.length() > 0) networks.getJSONObject(0).optString("name") else item.network
        val netId = if (networks != null && networks.length() > 0) networks.getJSONObject(0).optInt("id") else item.networkId

        val overviewAr = d.optString("overview").ifBlank {
            getJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$key&language=en")?.optString("overview").orEmpty()
        }

        item.copy(
            imdbId = imdbId,
            tmdbId = tmdbId,
            titleAr = (if (isTv) d.optString("name") else d.optString("title")).ifBlank { item.titleAr },
            titleEn = (if (isTv) d.optString("original_name") else d.optString("original_title")).ifBlank { item.titleEn },
            posterUrl = d.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$imgBase$it" } ?: item.posterUrl,
            backdropUrl = d.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$backdropBase$it" } ?: item.backdropUrl,
            logoUrl = logoUrl,
            overviewAr = overviewAr.ifBlank { item.overviewAr },
            rating = d.optDouble("vote_average", item.rating),
            genres = genres,
            network = netName,
            networkId = netId,
            seasonsCount = d.optInt("number_of_seasons", 1).coerceAtLeast(1),
            cast = cast
        )
    }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int, apiKey: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        val json = getJson("https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$apiKey&language=ar") ?: return@withContext emptyList()
        val eps = json.optJSONArray("episodes") ?: return@withContext emptyList()
        (0 until eps.length()).map { i ->
            val e = eps.getJSONObject(i)
            val still = e.optString("still_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$imgBase$it" } ?: ""
            EpisodeItem(season, e.optInt("episode_number", i + 1), e.optString("name", "الحلقة ${i + 1}"), e.optString("overview"), e.optString("air_date"), still, e.optInt("runtime", 24))
        }
    }

    suspend fun getPersonDetails(personId: Int, settings: AppSettings): PersonDetails? = withContext(Dispatchers.IO) {
        val json = getJson("https://api.themoviedb.org/3/person/$personId?api_key=${settings.tmdbApiKey}&language=ar&append_to_response=combined_credits") ?: return@withContext null
        val p = json.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }?.let { "$imgBase$it" } ?: ""
        val castArr = json.optJSONObject("combined_credits")?.optJSONArray("cast")
        val credits = mutableListOf<MediaItem>()
        if (castArr != null) {
            for (i in 0 until minOf(castArr.length(), 20)) {
                val obj = castArr.getJSONObject(i)
                val mt = obj.optString("media_type")
                if (mt == "movie" || mt == "tv") credits.add(parseMedia(obj, mt))
            }
        }
        PersonDetails(personId, json.optString("name"), json.optString("biography"), json.optString("birthday"), json.optString("place_of_birth"), p, credits.distinctBy { it.id })
    }

    suspend fun fetchArtworkOptions(tmdbId: Int, type: String, apiKey: String): ArtworkOptions = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext ArtworkOptions()
        val tmdbType = if (type == "series") "tv" else "movie"
        val json = getJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId/images?api_key=$apiKey") ?: return@withContext ArtworkOptions()
        fun extract(key: String, base: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return (0 until minOf(arr.length(), 10)).map { "$base${arr.getJSONObject(it).optString("file_path")}" }
        }
        ArtworkOptions(extract("posters", imgBase), extract("backdrops", backdropBase), extract("logos", imgBase))
    }
}
