package com.filmr.tv.engine

import com.filmr.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TmdbArtworkEngine {
    private val imgBase = "https://image.tmdb.org/t/p"

    private fun fetchText(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
    } catch (_: Exception) { null }

    private fun fetchJson(url: String): JSONObject? = fetchText(url)?.let { try { JSONObject(it) } catch (_: Exception) { null } }

    suspend fun translateToArabicIfNeeded(text: String, enabled: Boolean): String = withContext(Dispatchers.IO) {
        if (!enabled || text.isBlank() || text.any { it in '\u0600'..'\u06FF' }) return@withContext text
        try {
            val q = URLEncoder.encode(text.take(450), "UTF-8")
            val raw = fetchText("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ar&dt=t&q=$q") ?: return@withContext text
            val outer = JSONArray(raw).getJSONArray(0)
            buildString { for (i in 0 until outer.length()) append(outer.getJSONArray(i).optString(0)) }.ifBlank { text }
        } catch (_: Exception) { text }
    }

    suspend fun fetchMdbListRating(imdbId: String, apiKey: String): String = withContext(Dispatchers.IO) {
        if (imdbId.isBlank() || apiKey.isBlank()) return@withContext ""
        val ratings = fetchJson("https://mdblist.com/api/?apikey=$apiKey&i=$imdbId")?.optJSONArray("ratings") ?: return@withContext ""
        val parts = mutableListOf<String>()
        for (i in 0 until ratings.length()) {
            val r = ratings.getJSONObject(i)
            val src = r.optString("source")
            val valNum = r.optDouble("value", 0.0)
            if (valNum > 0 && (src == "imdb" || src == "trakt" || src == "tomatoes")) parts.add("${src.uppercase()}: $valNum")
        }
        parts.take(2).joinToString(" • ")
    }

    suspend fun getTrending(type: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbType = if (type == "series") "tv" else "movie"
        fetchJson("https://api.themoviedb.org/3/trending/$tmdbType/week?api_key=$apiKey&language=ar")?.let { parseResults(it, type) } ?: emptyList()
    }

    suspend fun getByNetwork(networkId: Int, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        fetchJson("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=ar&with_networks=$networkId")?.let { parseResults(it, "series") } ?: emptyList()
    }

    suspend fun searchArabic(query: String, apiKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        fetchJson("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar&query=$q")?.let { parseResults(it, "movie") } ?: emptyList()
    }

    suspend fun enrichDetails(item: MediaItem, settings: AppSettings): MediaItem = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey
        val tmdbType = if (item.type == "series") "tv" else "movie"
        var tmdbId = item.tmdbId
        if (tmdbId == 0 && item.imdbId.isNotBlank()) {
            val find = fetchJson("https://api.themoviedb.org/3/find/${item.imdbId}?api_key=$apiKey&external_source=imdb_id&language=ar")
            val arr = if (tmdbType == "tv") find?.optJSONArray("tv_results") else find?.optJSONArray("movie_results")
            if (arr != null && arr.length() > 0) tmdbId = arr.getJSONObject(0).optInt("id", 0)
        }
        if (tmdbId == 0) return@withContext item
        val d = fetchJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=ar&append_to_response=credits,external_ids,images") ?: return@withContext item
        val imdb = d.optJSONObject("external_ids")?.optString("imdb_id", item.imdbId)?.takeIf { it.isNotBlank() && it != "null" } ?: item.imdbId
        val credits = d.optJSONObject("credits")
        val cast = credits?.optJSONArray("cast")?.let { arr ->
            List(minOf(arr.length(), 14)) { i ->
                val c = arr.getJSONObject(i)
                val p = c.optString("profile_path")
                CastMember(c.optInt("id"), c.optString("name"), c.optString("character"), if (p.isNotBlank() && p != "null") "$imgBase/w185$p" else "")
            }
        } ?: emptyList()
        val directors = mutableListOf<String>()
        credits?.optJSONArray("crew")?.let { arr ->
            for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("job") == "Director") directors.add(arr.getJSONObject(i).optString("name"))
        }
        val poster = d.optString("poster_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w342$it" else item.posterUrl }
        val backdrop = d.optString("backdrop_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w780$it" else item.backdropUrl }
        val logos = d.optJSONObject("images")?.optJSONArray("logos")
        val logoUrl = if (logos != null && logos.length() > 0) "$imgBase/w300${logos.getJSONObject(0).optString("file_path")}" else item.logoUrl
        val genres = d.optJSONArray("genres")?.let { arr -> List(arr.length()) { arr.getJSONObject(it).optString("name") } } ?: item.genres
        val nets = d.optJSONArray("networks") ?: d.optJSONArray("production_companies")
        val netName = if (nets != null && nets.length() > 0) nets.getJSONObject(0).optString("name") else ""
        val netId = if (nets != null && nets.length() > 0) nets.getJSONObject(0).optInt("id", 0) else 0
        val netLogo = if (nets != null && nets.length() > 0) nets.getJSONObject(0).optString("logo_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w185$it" else "" } else ""
        val runtime = d.optInt("runtime", d.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0)
        val translatedOverview = translateToArabicIfNeeded(d.optString("overview", item.overviewAr), settings.autoTranslateArabic)
        val mdb = if (settings.mdbListEnabled) fetchMdbListRating(imdb, settings.mdbListApiKey) else ""

        item.copy(
            tmdbId = tmdbId, imdbId = imdb,
            titleAr = d.optString("title", d.optString("name", item.titleAr)),
            titleEn = d.optString("original_title", d.optString("original_name", item.titleEn)),
            overviewAr = translatedOverview, posterUrl = poster, backdropUrl = backdrop, logoUrl = logoUrl,
            genres = genres, directors = directors.distinct().take(2), network = netName, networkId = netId, networkLogoUrl = netLogo,
            runtimeMinutes = runtime, seasonsCount = d.optInt("number_of_seasons", item.seasonsCount),
            rating = d.optDouble("vote_average", item.rating), mdbRating = mdb, cast = cast
        )
    }

    suspend fun getPersonDetails(personId: Int, settings: AppSettings): PersonDetails? = withContext(Dispatchers.IO) {
        val json = fetchJson("https://api.themoviedb.org/3/person/$personId?api_key=${settings.tmdbApiKey}&language=ar&append_to_response=combined_credits") ?: return@withContext null
        val p = json.optString("profile_path")
        val credits = mutableListOf<MediaItem>()
        json.optJSONObject("combined_credits")?.optJSONArray("cast")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 20)) {
                val r = arr.getJSONObject(i)
                val id = r.optInt("id")
                val poster = r.optString("poster_path")
                val backdrop = r.optString("backdrop_path")
                credits.add(MediaItem("tmdb:$id", "", id, if (r.optString("media_type") == "tv") "series" else "movie", r.optString("title", r.optString("name", "")), r.optString("original_title", r.optString("original_name", "")), if (poster.isNotBlank() && poster != "null") "$imgBase/w342$poster" else "", if (backdrop.isNotBlank() && backdrop != "null") "$imgBase/w780$backdrop" else "", "", r.optString("overview", ""), "", r.optDouble("vote_average", 0.0), "", r.optString("release_date", r.optString("first_air_date", "")).take(4)))
            }
        }
        PersonDetails(personId, json.optString("name", ""), translateToArabicIfNeeded(json.optString("biography", ""), settings.autoTranslateArabic), json.optString("birthday", ""), json.optString("place_of_birth", ""), if (p.isNotBlank() && p != "null") "$imgBase/w342$p" else "", credits.distinctBy { it.id })
    }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int, apiKey: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        val arr = fetchJson("https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$apiKey&language=ar")?.optJSONArray("episodes") ?: return@withContext emptyList()
        List(arr.length()) { i ->
            val e = arr.getJSONObject(i)
            val still = e.optString("still_path")
            EpisodeItem(season, e.optInt("episode_number", i + 1), e.optString("name", "الحلقة ${i + 1}"), e.optString("overview", ""), e.optString("air_date", ""), if (still.isNotBlank() && still != "null") "$imgBase/w300$still" else "", e.optInt("runtime", 24))
        }
    }

    suspend fun fetchArtworkOptions(tmdbId: Int, type: String, apiKey: String): ArtworkOptions = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext ArtworkOptions()
        val tmdbType = if (type == "series") "tv" else "movie"
        val json = fetchJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId/images?api_key=$apiKey") ?: return@withContext ArtworkOptions()
        fun extract(key: String, size: String): List<String> = json.optJSONArray(key)?.let { a -> List(minOf(a.length(), 15)) { "$imgBase/$size${a.getJSONObject(it).optString("file_path")}" } } ?: emptyList()
        ArtworkOptions(extract("posters", "w342"), extract("backdrops", "w780"), extract("logos", "w300"))
    }

    private fun parseResults(json: JSONObject, fallbackType: String): List<MediaItem> {
        val arr = json.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<MediaItem>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val mType = when (r.optString("media_type", fallbackType)) { "tv", "series" -> "series"; "movie" -> "movie"; else -> continue }
            val id = r.optInt("id")
            val poster = r.optString("poster_path")
            val backdrop = r.optString("backdrop_path")
            val rel = r.optString("release_date", r.optString("first_air_date", ""))
            out.add(MediaItem("tmdb:$id", "", id, mType, r.optString("title", r.optString("name", "")), r.optString("original_title", r.optString("original_name", "")), if (poster.isNotBlank() && poster != "null") "$imgBase/w342$poster" else "", if (backdrop.isNotBlank() && backdrop != "null") "$imgBase/w780$backdrop" else "", "", r.optString("overview", ""), "", r.optDouble("vote_average", 0.0), "", rel.take(4), rel))
        }
        return out
    }
}
