package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TmdbMetadataService {
    private val apiKey = "439c478a771f35c05022f9feabcca01c"
    private val baseUrl = "https://api.themoviedb.org/3"
    private val imgBase = "https://image.tmdb.org/t/p"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private fun getJson(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            res.body?.string()?.let { JSONObject(it) }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun fetchTrendingRows(settings: AppSettings): List<AddonCatalogRow> = withContext(Dispatchers.IO) {
        if (!settings.tmdbEnabled || !settings.tmdbApplyHome) return@withContext emptyList()
        val lang = settings.tmdbLanguage
        val rows = mutableListOf<AddonCatalogRow>()

        val moviesJson = getJson("$baseUrl/trending/movie/week?api_key=$apiKey&language=$lang")
        val movies = parseTmdbList(moviesJson?.optJSONArray("results"), "movie")
        if (movies.isNotEmpty()) {
            rows.add(AddonCatalogRow("tmdb", "TMDB", "trending_movies", "فيلم - New", "ar", movies))
        }

        val tvJson = getJson("$baseUrl/trending/tv/week?api_key=$apiKey&language=$lang")
        val shows = parseTmdbList(tvJson?.optJSONArray("results"), "series")
        if (shows.isNotEmpty()) {
            rows.add(AddonCatalogRow("tmdb", "TMDB", "trending_tv", "مسلسل - New", "ar", shows))
        }
        rows
    }

    suspend fun searchTmdb(query: String, settings: AppSettings): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val json = getJson("$baseUrl/search/multi?api_key=$apiKey&language=${settings.tmdbLanguage}&query=$encoded")
        parseTmdbList(json?.optJSONArray("results"), "movie")
    }

    suspend fun resolveImdbIdOnly(item: MediaItem): String = withContext(Dispatchers.IO) {
        if (item.imdbId.startsWith("tt")) return@withContext item.imdbId
        val isSeries = item.type == "series" || item.type == "tv"
        val mediaType = if (isSeries) "tv" else "movie"
        var tmdbId = item.tmdbId
        if (tmdbId == 0 && item.id.startsWith("tmdb_")) {
            tmdbId = item.id.removePrefix("tmdb_").toIntOrNull() ?: 0
        }
        if (tmdbId == 0) {
            val q = URLEncoder.encode(item.titleEn.ifBlank { item.titleAr }, "UTF-8")
            val searchJson = getJson("$baseUrl/search/$mediaType?api_key=$apiKey&query=$q")
            tmdbId = searchJson?.optJSONArray("results")?.optJSONObject(0)?.optInt("id") ?: 0
        }
        if (tmdbId == 0) return@withContext item.id
        val ext = getJson("$baseUrl/$mediaType/$tmdbId/external_ids?api_key=$apiKey")
        ext?.optString("imdb_id")?.takeIf { it.startsWith("tt") } ?: item.id
    }

    suspend fun enrichItem(item: MediaItem, settings: AppSettings): MediaItem = withContext(Dispatchers.IO) {
        val isSeries = item.type == "series" || item.type == "tv"
        val mediaType = if (isSeries) "tv" else "movie"
        var tmdbId = item.tmdbId
        if (tmdbId == 0 && item.id.startsWith("tmdb_")) {
            tmdbId = item.id.removePrefix("tmdb_").toIntOrNull() ?: 0
        }

        if (tmdbId == 0 && item.imdbId.startsWith("tt")) {
            val findJson = getJson("$baseUrl/find/${item.imdbId}?api_key=$apiKey&external_source=imdb_id")
            val arr = if (isSeries) findJson?.optJSONArray("tv_results") else findJson?.optJSONArray("movie_results")
            tmdbId = arr?.optJSONObject(0)?.optInt("id") ?: 0
        }
        if (tmdbId == 0) {
            val q = URLEncoder.encode(item.titleEn.ifBlank { item.titleAr }, "UTF-8")
            val searchJson = getJson("$baseUrl/search/$mediaType?api_key=$apiKey&query=$q")
            tmdbId = searchJson?.optJSONArray("results")?.optJSONObject(0)?.optInt("id") ?: 0
        }
        if (tmdbId == 0) {
            val translatedOverview = AutoTranslateService.translateToArabicIfNeeded(item.overviewAr, settings.autoTranslateArabic)
            return@withContext item.copy(overviewAr = translatedOverview)
        }

        val details = getJson(
            "$baseUrl/$mediaType/$tmdbId?api_key=$apiKey&language=${settings.tmdbLanguage}&append_to_response=external_ids,credits,images,recommendations,videos&include_image_language=ar,en,null"
        ) ?: return@withContext item

        val extIds = details.optJSONObject("external_ids")
        val imdbId = extIds?.optString("imdb_id")?.takeIf { it.startsWith("tt") } ?: item.imdbId
        var titleAr = details.optString("title", details.optString("name", item.titleAr))
        val titleEn = details.optString("original_title", details.optString("original_name", item.titleEn))
        var overview = details.optString("overview")
        if (overview.isBlank()) {
            val enDetails = getJson("$baseUrl/$mediaType/$tmdbId?api_key=$apiKey&language=en-US")
            overview = enDetails?.optString("overview") ?: item.overviewAr
        }
        if (settings.autoTranslateArabic && overview.isNotBlank()) {
            overview = AutoTranslateService.translateToArabicIfNeeded(overview, true)
        }

        val posterPath = details.optString("poster_path")
        val backdropPath = details.optString("backdrop_path")
        val logos = details.optJSONObject("images")?.optJSONArray("logos")
        val logoPath = logos?.optJSONObject(0)?.optString("file_path") ?: ""

        val genresList = mutableListOf<String>()
        val gArr = details.optJSONArray("genres")
        if (gArr != null) {
            for (i in 0 until gArr.length()) {
                val gName = gArr.optJSONObject(i)?.optString("name") ?: ""
                if (gName.isNotBlank()) genresList.add(gName)
            }
        }

        var directorName = ""
        val crewArr = details.optJSONObject("credits")?.optJSONArray("crew")
        if (crewArr != null) {
            for (i in 0 until crewArr.length()) {
                val c = crewArr.getJSONObject(i)
                val job = c.optString("job")
                if (job == "Director" || job == "Executive Producer") {
                    directorName = c.optString("name")
                    if (job == "Director") break
                }
            }
        }

        val castList = mutableListOf<CastMember>()
        if (settings.tmdbCast) {
            val castArr = details.optJSONObject("credits")?.optJSONArray("cast")
            if (castArr != null) {
                for (i in 0 until minOf(15, castArr.length())) {
                    val c = castArr.getJSONObject(i)
                    val prof = c.optString("profile_path")
                    castList.add(
                        CastMember(
                            id = c.optInt("id"),
                            name = c.optString("name"),
                            character = c.optString("character"),
                            profileUrl = if (prof.isNotBlank()) "$imgBase/w185$prof" else ""
                        )
                    )
                }
            }
        }

        val companiesList = mutableListOf<CompanyItem>()
        if (settings.tmdbCompanies) {
            val compArr = details.optJSONArray("production_companies")
            if (compArr != null) {
                for (i in 0 until minOf(6, compArr.length())) {
                    val comp = compArr.getJSONObject(i)
                    val lPath = comp.optString("logo_path")
                    companiesList.add(
                        CompanyItem(
                            id = comp.optInt("id"),
                            name = comp.optString("name"),
                            logoUrl = if (lPath.isNotBlank()) "$imgBase/w185$lPath" else "",
                            originCountry = comp.optString("origin_country"),
                            isNetwork = false
                        )
                    )
                }
            }
        }

        if (settings.tmdbNetworks && isSeries) {
            val netArr = details.optJSONArray("networks")
            if (netArr != null) {
                for (i in 0 until minOf(4, netArr.length())) {
                    val net = netArr.optJSONObject(i) ?: continue
                    val lPath = net.optString("logo_path")
                    companiesList.add(
                        CompanyItem(
                            id = net.optInt("id"),
                            name = net.optString("name"),
                            logoUrl = if (lPath.isNotBlank()) "$imgBase/w185$lPath" else "",
                            originCountry = net.optString("origin_country"),
                            isNetwork = true
                        )
                    )
                }
            }
        }

        var trailerKey = ""
        val vids = details.optJSONObject("videos")?.optJSONArray("results")
        if (vids != null) {
            for (i in 0 until vids.length()) {
                val v = vids.optJSONObject(i) ?: continue
                if (v.optString("site") == "YouTube" && v.optString("type") == "Trailer") {
                    trailerKey = v.optString("key")
                    break
                }
            }
        }

        val recs = if (settings.tmdbRecommendations) {
            parseTmdbList(details.optJSONObject("recommendations")?.optJSONArray("results"), item.type)
        } else emptyList()

        val seasonsCount = details.optInt("number_of_seasons", 1).coerceAtLeast(1)
        val episodes = if (isSeries && settings.tmdbEpisodes) {
            fetchSeasonEpisodes(tmdbId, 1, settings.tmdbLanguage, settings.autoTranslateArabic)
        } else emptyList()

        val voteAvg = details.optDouble("vote_average", item.rating)
        val tmdbScore = if (voteAvg > 0) "${(voteAvg * 10).toInt()}%" else ""
        val relDate = details.optString("release_date", details.optString("first_air_date"))
        val origLang = details.optString("original_language").uppercase()
        val runtime = details.optInt("runtime", details.optJSONArray("episode_run_time")?.optInt(0) ?: item.runtimeMinutes)

        var enriched = item.copy(
            tmdbId = tmdbId,
            imdbId = imdbId,
            titleAr = if (settings.tmdbEnabled && settings.tmdbBasicInfo) titleAr.ifBlank { item.titleAr } else item.titleAr,
            titleEn = titleEn.ifBlank { item.titleEn },
            posterUrl = if (settings.tmdbEnabled && settings.tmdbArtwork && posterPath.isNotBlank()) "$imgBase/w500$posterPath" else item.posterUrl,
            backdropUrl = if (settings.tmdbEnabled && settings.tmdbArtwork && backdropPath.isNotBlank()) "$imgBase/w1280$backdropPath" else item.backdropUrl,
            logoUrl = if (settings.tmdbEnabled && settings.tmdbArtwork && logoPath.isNotBlank()) "$imgBase/w500$logoPath" else item.logoUrl,
            overviewAr = if (settings.tmdbEnabled && settings.tmdbBasicInfo) overview.ifBlank { item.overviewAr } else item.overviewAr,
            year = relDate.take(4).ifBlank { item.year },
            releaseDate = relDate,
            originalLang = origLang,
            director = directorName,
            runtimeMinutes = runtime,
            seasonsCount = seasonsCount,
            episodes = episodes,
            cast = castList,
            companies = companiesList,
            recommendations = recs,
            trailerKey = trailerKey,
            genres = genresList,
            rating = voteAvg,
            tmdbRating = tmdbScore,
            imdbRating = item.imdbRating.ifBlank { if (voteAvg > 0) String.format("%.1f", voteAvg) else "" }
        )

        if (settings.mdbListEnabled && settings.mdbListApiKey.isNotBlank() && imdbId.startsWith("tt")) {
            enriched = fetchMdbListRatings(enriched, settings.mdbListApiKey)
        }
        enriched
    }

    suspend fun fetchSeasonEpisodes(tmdbId: Int, seasonNumber: Int, language: String, autoTranslate: Boolean): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (tmdbId == 0) return@withContext emptyList()
        val json = getJson("$baseUrl/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey&language=$language")
            ?: return@withContext emptyList()
        val arr = json.optJSONArray("episodes") ?: return@withContext emptyList()
        val list = mutableListOf<EpisodeItem>()
        for (i in 0 until arr.length()) {
            val ep = arr.getJSONObject(i)
            val still = ep.optString("still_path")
            val vote = ep.optDouble("vote_average", 0.0)
            var epOverview = ep.optString("overview")
            if (autoTranslate && epOverview.isNotBlank()) {
                epOverview = AutoTranslateService.translateToArabicIfNeeded(epOverview, true)
            }
            list.add(
                EpisodeItem(
                    seasonNumber = seasonNumber,
                    episodeNumber = ep.optInt("episode_number", i + 1),
                    name = ep.optString("name", "الحلقة ${i + 1}"),
                    overview = epOverview,
                    stillUrl = if (still.isNotBlank()) "$imgBase/w500$still" else "",
                    airDate = ep.optString("air_date"),
                    runtime = ep.optInt("runtime", 0),
                    rating = if (vote > 0) String.format("%.1f", vote) else ""
                )
            )
        }
        list
    }

    suspend fun fetchPersonDetails(personId: Int, language: String, autoTranslate: Boolean): PersonDetails? = withContext(Dispatchers.IO) {
        val json = getJson("$baseUrl/person/$personId?api_key=$apiKey&language=$language&append_to_response=combined_credits")
            ?: return@withContext null
        val prof = json.optString("profile_path")
        var bio = json.optString("biography")
        if (bio.isBlank()) {
            val enJson = getJson("$baseUrl/person/$personId?api_key=$apiKey&language=en-US")
            bio = enJson?.optString("biography") ?: ""
        }
        if (autoTranslate && bio.isNotBlank()) {
            bio = AutoTranslateService.translateToArabicIfNeeded(bio, true)
        }
        val credits = parseTmdbList(json.optJSONObject("combined_credits")?.optJSONArray("cast"), "movie")
        PersonDetails(
            id = personId,
            name = json.optString("name"),
            biography = bio,
            birthday = json.optString("birthday"),
            placeOfBirth = json.optString("place_of_birth"),
            profileUrl = if (prof.isNotBlank()) "$imgBase/w300$prof" else "",
            knownFor = credits
        )
    }

    suspend fun discoverByCompanyOrNetwork(company: CompanyItem, sortBy: String, language: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val endpoint = if (company.isNetwork) "tv" else "movie"
        val param = if (company.isNetwork) "with_networks=${company.id}" else "with_companies=${company.id}"
        val json = getJson("$baseUrl/discover/$endpoint?api_key=$apiKey&language=$language&$param&sort_by=$sortBy")
        parseTmdbList(json?.optJSONArray("results"), if (company.isNetwork) "series" else "movie")
    }

    private fun fetchMdbListRatings(item: MediaItem, apiKey: String): MediaItem {
        val json = getJson("https://mdblist.com/api/?apikey=$apiKey&i=${item.imdbId}") ?: return item
        val ratings = json.optJSONArray("ratings") ?: return item
        var imdb = item.imdbRating
        var rt = ""
        var audience = ""
        var mc = ""
        var lb = ""
        var mal = ""
        for (i in 0 until ratings.length()) {
            val r = ratings.getJSONObject(i)
            val src = r.optString("source")
            val valStr = r.optString("value")
            if (valStr.isBlank() || valStr == "null") continue
            when (src) {
                "imdb" -> imdb = valStr
                "tomatoes" -> rt = "$valStr%"
                "popcorn" -> audience = "$valStr%"
                "metacritic" -> mc = valStr
                "letterboxd" -> lb = valStr
                "myanimelist" -> mal = valStr
            }
        }
        return item.copy(
            imdbRating = imdb,
            rtRating = rt,
            audienceRating = audience,
            metacriticRating = mc,
            letterboxdRating = lb,
            malRating = mal
        )
    }

    private fun parseTmdbList(arr: JSONArray?, defaultType: String): List<MediaItem> {
        if (arr == null) return emptyList()
        val list = mutableListOf<MediaItem>()
        for (i in 0 until minOf(25, arr.length())) {
            val m = arr.optJSONObject(i) ?: continue
            val mType = m.optString("media_type", defaultType)
            if (mType == "person") continue
            val id = m.optInt("id")
            if (id == 0) continue
            val title = m.optString("title", m.optString("name"))
            val origTitle = m.optString("original_title", m.optString("original_name", title))
            val poster = m.optString("poster_path")
            val backdrop = m.optString("backdrop_path")
            val date = m.optString("release_date", m.optString("first_air_date")).take(4)
            val vote = m.optDouble("vote_average", 0.0)
            val normType = if (mType == "tv" || mType == "series") "series" else "movie"
            list.add(
                MediaItem(
                    id = "tmdb_$id",
                    tmdbId = id,
                    type = normType,
                    titleAr = title,
                    titleEn = origTitle,
                    posterUrl = if (poster.isNotBlank()) "$imgBase/w500$poster" else "",
                    backdropUrl = if (backdrop.isNotBlank()) "$imgBase/w1280$backdrop" else "",
                    overviewAr = m.optString("overview"),
                    year = date,
                    rating = vote,
                    tmdbRating = if (vote > 0) "${(vote * 10).toInt()}%" else "",
                    imdbRating = if (vote > 0) String.format("%.1f", vote) else "",
                    sourceAddon = "TMDB"
                )
            )
        }
        return list
    }
}
