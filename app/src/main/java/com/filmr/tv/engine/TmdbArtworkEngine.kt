package com.filmr.tv.engine

import com.filmr.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class TmdbArtworkEngine {
    private val imgBase = "https://image.tmdb.org/t/p"

    private fun fetchJson(url: String): JSONObject? = try {
        JSONObject(URL(url).readText())
    } catch (_: Exception) { null }

    suspend fun getTrending(type: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbType = if (type == "series") "tv" else "movie"
        val json = fetchJson("https://api.themoviedb.org/3/trending/$tmdbType/week?api_key=$apiKey&language=ar") ?: return@withContext emptyList()
        parseResults(json, type)
    }

    suspend fun getByNetwork(networkId: Int, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = fetchJson("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=ar&with_networks=$networkId") ?: return@withContext emptyList()
        parseResults(json, "series")
    }

    suspend fun searchArabic(query: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = fetchJson("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar&query=$q") ?: return@withContext emptyList()
        parseResults(json, "movie")
    }

    suspend fun enrichDetails(item: MediaItem, apiKey: String): MediaItem = withContext(Dispatchers.IO) {
        val tmdbType = if (item.type == "series") "tv" else "movie"
        var tmdbId = item.tmdbId
        if (tmdbId == 0 && item.imdbId.isNotBlank()) {
            val find = fetchJson("https://api.themoviedb.org/3/find/${item.imdbId}?api_key=$apiKey&external_source=imdb_id&language=ar")
            val arr = if (tmdbType == "tv") find?.optJSONArray("tv_results") else find?.optJSONArray("movie_results")
            if (arr != null && arr.length() > 0) tmdbId = arr.getJSONObject(0).optInt("id", 0)
        }
        if (tmdbId == 0) return@withContext item
        val d = fetchJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=ar&append_to_response=credits,external_ids") ?: return@withContext item
        val imdb = d.optJSONObject("external_ids")?.optString("imdb_id", item.imdbId) ?: item.imdbId
        val castArr = d.optJSONObject("credits")?.optJSONArray("cast")
        val cast = if (castArr != null) List(minOf(castArr.length(), 12)) { i ->
            val c = castArr.getJSONObject(i)
            val p = c.optString("profile_path")
            CastMember(c.optInt("id"), c.optString("name"), c.optString("character"), if (p.isNotBlank() && p != "null") "$imgBase/w185$p" else "")
        } else emptyList()
        val poster = d.optString("poster_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w500$it" else item.posterUrl }
        val backdrop = d.optString("backdrop_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w1280$it" else item.backdropUrl }
        item.copy(
            tmdbId = tmdbId,
            imdbId = imdb,
            titleAr = d.optString("title", d.optString("name", item.titleAr)),
            titleEn = d.optString("original_title", d.optString("original_name", item.titleEn)),
            overviewAr = d.optString("overview", item.overviewAr),
            posterUrl = poster,
            backdropUrl = backdrop,
            seasonsCount = d.optInt("number_of_seasons", item.seasonsCount),
            rating = d.optDouble("vote_average", item.rating),
            cast = cast
        )
    }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int, apiKey: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        val json = fetchJson("https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$apiKey&language=ar") ?: return@withContext emptyList()
        val arr = json.optJSONArray("episodes") ?: return@withContext emptyList()
        List(arr.length()) { i ->
            val e = arr.getJSONObject(i)
            val still = e.optString("still_path")
            EpisodeItem(
                season = season,
                episode = e.optInt("episode_number", i + 1),
                title = e.optString("name", "الحلقة ${i + 1}"),
                overview = e.optString("overview", ""),
                released = e.optString("air_date", ""),
                stillUrl = if (still.isNotBlank() && still != "null") "$imgBase/w500$still" else ""
            )
        }
    }

    suspend fun fetchArtworkOptions(tmdbId: Int, type: String, apiKey: String): ArtworkOptions = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext ArtworkOptions()
        val tmdbType = if (type == "series") "tv" else "movie"
        val json = fetchJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId/images?api_key=$apiKey") ?: return@withContext ArtworkOptions()
        fun extract(key: String, size: String): List<String> {
            val a = json.optJSONArray(key) ?: return emptyList()
            return List(minOf(a.length(), 15)) { "$imgBase/$size${a.getJSONObject(it).optString("file_path")}" }
        }
        ArtworkOptions(extract("posters", "w500"), extract("backdrops", "w1280"), extract("logos", "w500"))
    }

    private fun parseResults(json: JSONObject, fallbackType: String): List<MediaItem> {
        val arr = json.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<MediaItem>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val mType = when (r.optString("media_type", fallbackType)) {
                "tv", "series" -> "series"
                "movie" -> "movie"
                else -> continue
            }
            val id = r.optInt("id")
            val poster = r.optString("poster_path")
            val backdrop = r.optString("backdrop_path")
            out.add(
                MediaItem(
                    id = "tmdb:$id",
                    tmdbId = id,
                    type = mType,
                    titleAr = r.optString("title", r.optString("name", "")),
                    titleEn = r.optString("original_title", r.optString("original_name", "")),
                    posterUrl = if (poster.isNotBlank() && poster != "null") "$imgBase/w500$poster" else "",
                    backdropUrl = if (backdrop.isNotBlank() && backdrop != "null") "$imgBase/w1280$backdrop" else "",
                    overviewAr = r.optString("overview", ""),
                    rating = r.optDouble("vote_average", 0.0),
                    year = r.optString("release_date", r.optString("first_air_date", "")).take(4)
                )
            )
        }
        return out
    }
}
