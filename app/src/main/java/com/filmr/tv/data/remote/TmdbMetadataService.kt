package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TmdbMetadataService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val imgBase = "https://image.tmdb.org/t/p/w500"
    private val backdropBase = "https://image.tmdb.org/t/p/w1280"

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
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
        val posterPath = obj.optString("poster_path")
        val backdropPath = obj.optString("backdrop_path")
        return MediaItem(
            id = "tmdb_${if (isTv) "tv" else "movie"}_$tmdbId",
            tmdbId = tmdbId,
            type = if (isTv) "series" else "movie",
            titleAr = title.ifBlank { origTitle },
            titleEn = origTitle.ifBlank { title },
            posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "$imgBase$posterPath" else "",
            backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") "$backdropBase$backdropPath" else "",
            overviewAr = obj.optString("overview"),
            rating = obj.optDouble("vote_average", 0.0),
            tmdbRating = String.format("%.1f", obj.optDouble("vote_average", 0.0)),
            year = date.take(4),
            sourceAddon = "TMDB"
        )
    }

    suspend fun getTrending(type: String, apiKey: String, lang: String = "ar-SA"): List<MediaItem> = withContext(Dispatchers.IO) {
        val tmdbType = if (type == "series" || type == "tv") "tv" else "movie"
        val json = getJson("https://api.themoviedb.org/3/trending/$tmdbType/week?api_key=$apiKey&language=$lang")
            ?: return@withContext emptyList()
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseMedia(arr.getJSONObject(it), tmdbType) }
    }

    suspend fun searchMulti(query: String, apiKey: String, lang: String = "ar-SA"): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = getJson("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=$lang&query=$q&include_adult=false")
            ?: return@withContext emptyList()
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
        if (!settings.tmdbEnabled) return@withContext item
        val apiKey = settings.tmdbApiKey
        val lang = settings.tmdbLanguage
        val isTv = item.type == "series" || item.type == "tv"
        val tmdbType = if (isTv) "tv" else "movie"
        var tmdbId = item.tmdbId
        var imdbId = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }

        if (tmdbId == 0 && imdbId.isNotBlank()) {
            val find = getJson("https://api.themoviedb.org/3/find/$imdbId?api_key=$apiKey&external_source=imdb_id")
            val resArr = find?.optJSONArray(if (isTv) "tv_results" else "movie_results")
            if (resArr != null && resArr.length() > 0) tmdbId = resArr.getJSONObject(0).optInt("id")
        }
        if (tmdbId == 0) {
            val q = URLEncoder.encode(item.titleEn.ifBlank { item.titleAr }, "UTF-8")
            val s = getJson("https://api.themoviedb.org/3/search/$tmdbType?api_key=$apiKey&query=$q")
            val arr = s?.optJSONArray("results")
            if (arr != null && arr.length() > 0) tmdbId = arr.getJSONObject(0).optInt("id")
        }
        if (tmdbId == 0) return@withContext item

        val d = getJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=$lang&append_to_response=external_ids,credits,images,recommendations&include_image_language=ar,en,null")
            ?: return@withContext item

        val extImdb = d.optJSONObject("external_ids")?.optString("imdb_id").orEmpty()
        if (extImdb.startsWith("tt")) imdbId = extImdb

        val genres = mutableListOf<String>()
        d.optJSONArray("genres")?.let { gArr ->
            for (i in 0 until gArr.length()) genres.add(gArr.getJSONObject(i).optString("name"))
        }

        val cast = mutableListOf<CastMember>()
        if (settings.tmdbCast) {
            d.optJSONObject("credits")?.optJSONArray("cast")?.let { cArr ->
                for (i in 0 until minOf(15, cArr.length())) {
                    val c = cArr.getJSONObject(i)
                    val p = c.optString("profile_path")
                    cast.add(CastMember(
                        id = c.optInt("id"),
                        name = c.optString("name"),
                        character = c.optString("character"),
                        profileUrl = if (p.isNotBlank() && p != "null") "$imgBase$p" else null
                    ))
                }
            }
        }

        val companies = mutableListOf<CompanyItem>()
        if (settings.tmdbCompanies) {
            d.optJSONArray("production_companies")?.let { pArr ->
                for (i in 0 until minOf(8, pArr.length())) {
                    val p = pArr.getJSONObject(i)
                    val lp = p.optString("logo_path")
                    companies.add(CompanyItem(
                        id = p.optInt("id"),
                        name = p.optString("name"),
                        logoUrl = if (lp.isNotBlank() && lp != "null") "$imgBase$lp" else null
                    ))
                }
            }
        }

        val recs = mutableListOf<MediaItem>()
        if (settings.tmdbRecommendations) {
            d.optJSONObject("recommendations")?.optJSONArray("results")?.let { rArr ->
                for (i in 0 until minOf(12, rArr.length())) {
                    recs.add(parseMedia(rArr.getJSONObject(i), tmdbType))
                }
            }
        }

        val logos = d.optJSONObject("images")?.optJSONArray("logos")
        val logoUrl = if (settings.tmdbArtwork && logos != null && logos.length() > 0) {
            "$imgBase${logos.getJSONObject(0).optString("file_path")}"
        } else item.logoUrl

        val runtime = if (isTv) {
            d.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0
        } else {
            d.optInt("runtime", 0)
        }

        var overviewAr = d.optString("overview").ifBlank { item.overviewAr }
        if (overviewAr.isBlank()) {
            val enJson = getJson("https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=$apiKey&language=en-US")
            overviewAr = enJson?.optString("overview").orEmpty()
        }

        val posterPath = d.optString("poster_path")
        val backdropPath = d.optString("backdrop_path")
        val tmdbScore = d.optDouble("vote_average", item.rating)

        var imdbStr = item.imdbRating
        var rtStr = item.rtRating
        var mcStr = item.metacriticRating
        var lbStr = item.letterboxdRating
        var malStr = item.malRating

        if (settings.mdblistApiKey.isNotBlank() && imdbId.isNotBlank()) {
            val mdb = getJson("https://mdblist.com/api/?apikey=${settings.mdblistApiKey.trim()}&i=$imdbId")
            mdb?.optJSONArray("ratings")?.let { rArr ->
                for (i in 0 until rArr.length()) {
                    val r = rArr.getJSONObject(i)
                    val src = r.optString("source").lowercase()
                    val valStr = r.optString("value")
                    if (valStr.isNotBlank() && valStr != "null" && valStr != "0") {
                        when {
                            src.contains("imdb") -> imdbStr = valStr
                            src.contains("tomatoes") -> rtStr = "$valStr%"
                            src.contains("metacritic") -> mcStr = valStr
                            src.contains("letterboxd") -> lbStr = valStr
                            src.contains("mal") -> malStr = valStr
                        }
                    }
                }
            }
        }

        val netObj = d.optJSONArray("networks")?.optJSONObject(0)

        item.copy(
            imdbId = imdbId,
            tmdbId = tmdbId,
            titleAr = if (settings.tmdbBasicInfo) (if (isTv) d.optString("name") else d.optString("title")).ifBlank { item.titleAr } else item.titleAr,
            titleEn = if (isTv) d.optString("original_name").ifBlank { item.titleEn } else d.optString("original_title").ifBlank { item.titleEn },
            posterUrl = if (settings.tmdbArtwork && posterPath.isNotBlank() && posterPath != "null") "$imgBase$posterPath" else item.posterUrl,
            backdropUrl = if (settings.tmdbArtwork && backdropPath.isNotBlank() && backdropPath != "null") "$backdropBase$backdropPath" else item.backdropUrl,
            logoUrl = logoUrl,
            overviewAr = overviewAr,
            rating = tmdbScore,
            tmdbRating = if (tmdbScore > 0) String.format("%.1f", tmdbScore) else "",
            imdbRating = imdbStr,
            rtRating = rtStr,
            metacriticRating = mcStr,
            letterboxdRating = lbStr,
            malRating = malStr,
            genres = if (genres.isNotEmpty()) genres else item.genres,
            network = if (settings.tmdbNetworks) netObj?.optString("name").orEmpty() else "",
            networkId = netObj?.optInt("id") ?: 0,
            runtimeMinutes = if (runtime > 0) runtime else item.runtimeMinutes,
            seasonsCount = if (isTv) d.optInt("number_of_seasons", 1).coerceAtLeast(1) else 1,
            cast = cast,
            companies = companies,
            recommendations = recs
        )
    }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int, apiKey: String, lang: String = "ar-SA"): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        val json = getJson("https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$apiKey&language=$lang")
            ?: return@withContext emptyList()
        val arr = json.optJSONArray("episodes") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            val sp = e.optString("still_path")
            EpisodeItem(
                season = season,
                episode = e.optInt("episode_number", i + 1),
                title = e.optString("name").ifBlank { "الحلقة ${i + 1}" },
                overview = e.optString("overview"),
                rating = e.optDouble("vote_average", 0.0),
                stillUrl = if (sp.isNotBlank() && sp != "null") "$imgBase$sp" else "",
                runtime = e.optInt("runtime", 24)
            )
        }
    }

    suspend fun getPersonDetails(personId: Int, apiKey: String, lang: String = "ar-SA"): PersonDetails? = withContext(Dispatchers.IO) {
        val json = getJson("https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&language=$lang&append_to_response=combined_credits")
            ?: return@withContext null
        var bio = json.optString("biography")
        if (bio.isBlank()) {
            bio = getJson("https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&language=en-US")?.optString("biography").orEmpty()
        }
        val pp = json.optString("profile_path")
        val credits = mutableListOf<MediaItem>()
        json.optJSONObject("combined_credits")?.optJSONArray("cast")?.let { cArr ->
            for (i in 0 until minOf(24, cArr.length())) {
                val c = cArr.getJSONObject(i)
                val mt = c.optString("media_type")
                if (mt == "movie" || mt == "tv") credits.add(parseMedia(c, mt))
            }
        }
        PersonDetails(
            id = personId,
            name = json.optString("name"),
            biography = bio,
            birthday = json.optString("birthday"),
            placeOfBirth = json.optString("place_of_birth"),
            profileUrl = if (pp.isNotBlank() && pp != "null") "$imgBase$pp" else null,
            knownFor = credits.distinctBy { it.id }
        )
    }

    suspend fun discoverByCompanyOrNetwork(id: Int, isNetwork: Boolean, sortBy: String, apiKey: String, lang: String = "ar-SA"): List<MediaItem> = withContext(Dispatchers.IO) {
        val endpoint = if (isNetwork) "tv" else "movie"
        val filterParam = if (isNetwork) "with_networks=$id" else "with_companies=$id"
        val json = getJson("https://api.themoviedb.org/3/discover/$endpoint?api_key=$apiKey&language=$lang&$filterParam&sort_by=$sortBy")
            ?: return@withContext emptyList()
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { parseMedia(arr.getJSONObject(it), endpoint) }
    }
}
