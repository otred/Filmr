package com.filmr.tv.data.remote

import com.filmr.tv.data.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TmdbMetadataService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.themoviedb.org/3"
    private val imgBase = "https://image.tmdb.org/t/p"

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                JsonParser.parseString(body).asJsonObject
            }
        }.getOrNull()
    }

    suspend fun fetchHomeCatalogs(settings: AppSettings): List<AddonCatalogRow> {
        if (!settings.tmdbEnabled || settings.tmdbApiKey.isBlank()) return emptyList()
        val key = settings.tmdbApiKey
        val lang = settings.tmdbLanguage

        val rows = mutableListOf<AddonCatalogRow>()

        val trendingMovies = fetchList("$baseUrl/trending/movie/week?api_key=$key&language=$lang", "movie")
        if (trendingMovies.isNotEmpty()) {
            rows.add(
                AddonCatalogRow(
                    id = "tmdb_trending_movies",
                    title = "أفلام رائجة هذا الأسبوع",
                    addonName = "TMDB",
                    addonType = "tmdb",
                    items = trendingMovies
                )
            )
        }

        val trendingSeries = fetchList("$baseUrl/trending/tv/week?api_key=$key&language=$lang", "series")
        if (trendingSeries.isNotEmpty()) {
            rows.add(
                AddonCatalogRow(
                    id = "tmdb_trending_series",
                    title = "مسلسلات رائجة هذا الأسبوع",
                    addonName = "TMDB",
                    addonType = "tmdb",
                    items = trendingSeries
                )
            )
        }

        val popularMovies = fetchList("$baseUrl/movie/popular?api_key=$key&language=$lang", "movie")
        if (popularMovies.isNotEmpty()) {
            rows.add(
                AddonCatalogRow(
                    id = "tmdb_popular_movies",
                    title = "الأفلام الأكثر مشاهدة",
                    addonName = "TMDB",
                    addonType = "tmdb",
                    items = popularMovies
                )
            )
        }

        val topSeries = fetchList("$baseUrl/tv/top_rated?api_key=$key&language=$lang", "series")
        if (topSeries.isNotEmpty()) {
            rows.add(
                AddonCatalogRow(
                    id = "tmdb_top_series",
                    title = "مسلسلات الأعلى تقييماً",
                    addonName = "TMDB",
                    addonType = "tmdb",
                    items = topSeries
                )
            )
        }

        return rows
    }

    private suspend fun fetchList(url: String, defaultType: String): List<MediaItem> {
        val obj = getJson(url) ?: return emptyList()
        val results = obj.getAsJsonArray("results") ?: return emptyList()
        return results.mapNotNull { el ->
            parseMediaSummary(el.asJsonObject, defaultType)
        }
    }

    suspend fun searchMulti(query: String, settings: AppSettings): List<MediaItem> {
        if (!settings.tmdbEnabled || settings.tmdbApiKey.isBlank() || query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$baseUrl/search/multi?api_key=${settings.tmdbApiKey}&language=${settings.tmdbLanguage}&query=$q&include_adult=false"
        val obj = getJson(url) ?: return emptyList()
        val results = obj.getAsJsonArray("results") ?: return emptyList()
        return results.mapNotNull { el ->
            val item = el.asJsonObject
            val mediaType = item.get("media_type")?.asString ?: return@mapNotNull null
            if (mediaType != "movie" && mediaType != "tv") return@mapNotNull null
            parseMediaSummary(item, if (mediaType == "tv") "series" else "movie")
        }
    }

    suspend fun enrichMediaDetails(baseItem: MediaItem, settings: AppSettings): MediaItem {
        if (!settings.tmdbEnabled || settings.tmdbApiKey.isBlank()) return baseItem
        val key = settings.tmdbApiKey
        val lang = settings.tmdbLanguage
        val isSeries = baseItem.type == "series" || baseItem.type == "tv"
        val tmdbType = if (isSeries) "tv" else "movie"

        var tmdbId = baseItem.tmdbId ?: baseItem.id.removePrefix("tmdb:").toIntOrNull()
        if (tmdbId == null && !baseItem.imdbId.isNullOrBlank()) {
            val findObj = getJson("$baseUrl/find/${baseItem.imdbId}?api_key=$key&external_source=imdb_id")
            val arrKey = if (isSeries) "tv_results" else "movie_results"
            val arr = findObj?.getAsJsonArray(arrKey)
            if (arr != null && arr.size() > 0) {
                tmdbId = arr[0].asJsonObject.get("id")?.asInt
            }
        }
        if (tmdbId == null) {
            val titleQuery = baseItem.titleEn.ifBlank { baseItem.titleAr }
            if (titleQuery.isNotBlank()) {
                val q = URLEncoder.encode(titleQuery, "UTF-8")
                val searchObj = getJson("$baseUrl/search/$tmdbType?api_key=$key&query=$q")
                val arr = searchObj?.getAsJsonArray("results")
                if (arr != null && arr.size() > 0) {
                    tmdbId = arr[0].asJsonObject.get("id")?.asInt
                }
            }
        }
        if (tmdbId == null) return baseItem

        val detailsUrl = "$baseUrl/$tmdbType/$tmdbId?api_key=$key&language=$lang&append_to_response=external_ids,credits,images,recommendations&include_image_language=en,ar,null"
        val obj = getJson(detailsUrl) ?: return baseItem

        // Fallback English overview if Arabic is empty
        var overview = obj.get("overview")?.asString.orEmpty()
        var titleEn = obj.get(if (isSeries) "original_name" else "original_title")?.asString.orEmpty()
        if (overview.isBlank() || titleEn.isBlank()) {
            val enObj = getJson("$baseUrl/$tmdbType/$tmdbId?api_key=$key&language=en-US")
            if (overview.isBlank()) overview = enObj?.get("overview")?.asString.orEmpty()
            if (titleEn.isBlank()) titleEn = enObj?.get(if (isSeries) "name" else "title")?.asString.orEmpty()
        }

        val titleAr = obj.get(if (isSeries) "name" else "title")?.asString.orEmpty().ifBlank { baseItem.titleAr }
        val extIds = obj.getAsJsonObject("external_ids")
        val imdbId = extIds?.get("imdb_id")?.takeIf { !it.isJsonNull }?.asString ?: baseItem.imdbId

        val posterPath = obj.get("poster_path")?.takeIf { !it.isJsonNull }?.asString
        val backdropPath = obj.get("backdrop_path")?.takeIf { !it.isJsonNull }?.asString
        val posterUrl = if (settings.tmdbArtwork && posterPath != null) "$imgBase/w500$posterPath" else baseItem.posterUrl
        val backdropUrl = if (settings.tmdbArtwork && backdropPath != null) "$imgBase/w1280$backdropPath" else baseItem.backdropUrl

        var logoUrl: String? = baseItem.logoUrl
        if (settings.tmdbArtwork) {
            val logos = obj.getAsJsonObject("images")?.getAsJsonArray("logos")
            if (logos != null && logos.size() > 0) {
                val chosen = logos.firstOrNull {
                    val iso = it.asJsonObject.get("iso_639_1")?.takeIf { x -> !x.isJsonNull }?.asString
                    iso == "en" || iso == "ar"
                } ?: logos[0]
                val path = chosen.asJsonObject.get("file_path")?.asString
                if (!path.isNullOrBlank()) logoUrl = "$imgBase/w500$path"
            }
        }

        val voteAvg = obj.get("vote_average")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
        val tmdbRating = if (voteAvg > 0) String.format("%.1f", voteAvg) else baseItem.tmdbRating

        val dateStr = obj.get(if (isSeries) "first_air_date" else "release_date")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val year = dateStr.take(4).ifBlank { baseItem.year }

        val runtime = if (isSeries) {
            obj.getAsJsonArray("episode_run_time")?.firstOrNull()?.asInt ?: baseItem.runtimeMinutes
        } else {
            obj.get("runtime")?.takeIf { !it.isJsonNull }?.asInt ?: baseItem.runtimeMinutes
        }

        val genres = obj.getAsJsonArray("genres")?.mapNotNull {
            it.asJsonObject.get("name")?.asString
        } ?: baseItem.genres

        // Cast
        val castList = if (settings.tmdbCast) {
            obj.getAsJsonObject("credits")?.getAsJsonArray("cast")?.take(16)?.mapNotNull { c ->
                val co = c.asJsonObject
                val id = co.get("id")?.asInt ?: return@mapNotNull null
                val name = co.get("name")?.asString ?: return@mapNotNull null
                val character = co.get("character")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val pPath = co.get("profile_path")?.takeIf { !it.isJsonNull }?.asString
                CastMember(
                    id = id,
                    name = name,
                    character = character,
                    profileUrl = pPath?.let { "$imgBase/w185$it" }
                )
            } ?: baseItem.cast
        } else emptyList()

        // Companies & Networks
        val companyList = mutableListOf<CompanyItem>()
        if (settings.tmdbCompanies) {
            obj.getAsJsonArray("networks")?.forEach { n ->
                val no = n.asJsonObject
                val id = no.get("id")?.asInt ?: return@forEach
                val name = no.get("name")?.asString ?: return@forEach
                val lPath = no.get("logo_path")?.takeIf { !it.isJsonNull }?.asString
                companyList.add(CompanyItem(id, name, lPath?.let { "$imgBase/w185$it" }, isNetwork = true))
            }
            obj.getAsJsonArray("production_companies")?.take(6)?.forEach { c ->
                val co = c.asJsonObject
                val id = co.get("id")?.asInt ?: return@forEach
                val name = co.get("name")?.asString ?: return@forEach
                val lPath = co.get("logo_path")?.takeIf { !it.isJsonNull }?.asString
                companyList.add(CompanyItem(id, name, lPath?.let { "$imgBase/w185$it" }, isNetwork = false))
            }
        }

        // Recommendations
        val recs = if (settings.tmdbRecommendations) {
            obj.getAsJsonObject("recommendations")?.getAsJsonArray("results")?.take(14)?.mapNotNull { r ->
                parseMediaSummary(r.asJsonObject, if (isSeries) "series" else "movie")
            } ?: baseItem.recommendations
        } else emptyList()

        // Seasons & Episodes for Series
        val seasonsCount = obj.get("number_of_seasons")?.takeIf { !it.isJsonNull }?.asInt ?: baseItem.seasonsCount
        val episodes = if (isSeries && seasonsCount > 0) {
            fetchSeasonEpisodes(tmdbId, 1, imdbId ?: "tmdb:$tmdbId", settings)
        } else baseItem.episodes

        // MDBList Multi-Ratings Enrichment
        var imdbRating = baseItem.imdbRating
        var rtRating = baseItem.rtRating
        var mcRating = baseItem.metacriticRating
        var lbRating = baseItem.letterboxdRating
        var malRating = baseItem.malRating

        if (settings.mdblistApiKey.isNotBlank() && !imdbId.isNullOrBlank()) {
            val mdbObj = getJson("https://mdblist.com/api/?apikey=${settings.mdblistApiKey}&i=$imdbId")
            val ratingsArr = mdbObj?.getAsJsonArray("ratings")
            ratingsArr?.forEach { rEl ->
                val ro = rEl.asJsonObject
                val src = ro.get("source")?.asString?.lowercase().orEmpty()
                val valStr = ro.get("value")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                if (valStr == "0" || valStr == "null") return@forEach
                when (src) {
                    "imdb" -> imdbRating = valStr
                    "tomatoes" -> rtRating = "$valStr%"
                    "metacritic" -> mcRating = valStr
                    "letterboxd" -> lbRating = valStr
                    "myanimelist" -> malRating = valStr
                }
            }
        }

        return baseItem.copy(
            id = imdbId ?: "tmdb:$tmdbId",
            imdbId = imdbId,
            tmdbId = tmdbId,
            titleAr = titleAr,
            titleEn = titleEn,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            overview = overview,
            year = year,
            runtimeMinutes = runtime,
            seasonsCount = seasonsCount,
            genres = genres,
            episodes = episodes,
            cast = castList,
            companies = companyList,
            recommendations = recs,
            imdbRating = imdbRating,
            tmdbRating = tmdbRating,
            rtRating = rtRating,
            metacriticRating = mcRating,
            letterboxdRating = lbRating,
            malRating = malRating
        )
    }

    suspend fun fetchSeasonEpisodes(
        tmdbId: Int,
        seasonNumber: Int,
        seriesExternalId: String,
        settings: AppSettings
    ): List<EpisodeItem> {
        val key = settings.tmdbApiKey
        val lang = settings.tmdbLanguage
        val obj = getJson("$baseUrl/tv/$tmdbId/season/$seasonNumber?api_key=$key&language=$lang") ?: return emptyList()
        val eps = obj.getAsJsonArray("episodes") ?: return emptyList()
        return eps.mapNotNull { el ->
            val eo = el.asJsonObject
            val epNum = eo.get("episode_number")?.asInt ?: return@mapNotNull null
            val name = eo.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "الحلقة $epNum"
            val overview = eo.get("overview")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val stillPath = eo.get("still_path")?.takeIf { !it.isJsonNull }?.asString
            val airDate = eo.get("air_date")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val runtime = eo.get("runtime")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            EpisodeItem(
                id = "$seriesExternalId:$seasonNumber:$epNum",
                season = seasonNumber,
                episode = epNum,
                title = name,
                overview = overview,
                thumbnailUrl = stillPath?.let { "$imgBase/w500$it" },
                released = airDate,
                runtimeMinutes = runtime
            )
        }
    }

    suspend fun fetchPersonDetails(personId: Int, settings: AppSettings): PersonDetails? {
        val key = settings.tmdbApiKey
        val obj = getJson("$baseUrl/person/$personId?api_key=$key&language=${settings.tmdbLanguage}&append_to_response=combined_credits") ?: return null
        val name = obj.get("name")?.asString ?: return null
        val bio = obj.get("biography")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val bday = obj.get("birthday")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val place = obj.get("place_of_birth")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val pPath = obj.get("profile_path")?.takeIf { !it.isJsonNull }?.asString
        val castCredits = obj.getAsJsonObject("combined_credits")?.getAsJsonArray("cast")?.take(24)?.mapNotNull { el ->
            val co = el.asJsonObject
            val mType = co.get("media_type")?.asString ?: "movie"
            if (mType != "movie" && mType != "tv") return@mapNotNull null
            parseMediaSummary(co, if (mType == "tv") "series" else "movie")
        } ?: emptyList()

        return PersonDetails(
            id = personId,
            name = name,
            biography = bio,
            birthday = bday,
            placeOfBirth = place,
            profileUrl = pPath?.let { "$imgBase/w342$it" },
            credits = castCredits
        )
    }

    suspend fun discoverByCompanyOrNetwork(
        id: Int,
        isNetwork: Boolean,
        sortBy: String,
        settings: AppSettings
    ): List<MediaItem> {
        val key = settings.tmdbApiKey
        val lang = settings.tmdbLanguage
        return if (isNetwork) {
            fetchList("$baseUrl/discover/tv?api_key=$key&language=$lang&with_networks=$id&sort_by=$sortBy", "series")
        } else {
            val movies = fetchList("$baseUrl/discover/movie?api_key=$key&language=$lang&with_companies=$id&sort_by=$sortBy", "movie")
            val series = fetchList("$baseUrl/discover/tv?api_key=$key&language=$lang&with_companies=$id&sort_by=$sortBy", "series")
            (movies + series).take(24)
        }
    }

    private fun parseMediaSummary(obj: JsonObject, defaultType: String): MediaItem? {
        val id = obj.get("id")?.asInt ?: return null
        val isSeries = defaultType == "series" || defaultType == "tv"
        val titleAr = obj.get(if (isSeries) "name" else "title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val titleEn = obj.get(if (isSeries) "original_name" else "original_title")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        if (titleAr.isBlank() && titleEn.isBlank()) return null

        val posterPath = obj.get("poster_path")?.takeIf { !it.isJsonNull }?.asString
        val backdropPath = obj.get("backdrop_path")?.takeIf { !it.isJsonNull }?.asString
        val overview = obj.get("overview")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val dateStr = obj.get(if (isSeries) "first_air_date" else "release_date")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val vote = obj.get("vote_average")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0

        return MediaItem(
            id = "tmdb:$id",
            tmdbId = id,
            type = if (isSeries) "series" else "movie",
            titleAr = titleAr,
            titleEn = titleEn,
            posterUrl = posterPath?.let { "$imgBase/w342$it" },
            backdropUrl = backdropPath?.let { "$imgBase/w1280$it" },
            overview = overview,
            year = dateStr.take(4),
            tmdbRating = if (vote > 0) String.format("%.1f", vote) else null
        )
    }
}
