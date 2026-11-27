package com.filmr.tv.data.remote

import com.filmr.tv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

class MetadataService {

    companion object {
        const val IMG_W500 = "https://image.tmdb.org/t/p/w500"
        const val IMG_ORIGINAL = "https://image.tmdb.org/t/p/original"
    }

    suspend fun getTrendingHeroAndRows(apiKey: String, autoTranslate: Boolean): Map<String, List<MediaItem>> = coroutineScope {
        val trendingAll = async { fetchTmdbList("https://api.themoviedb.org/3/trending/all/day?api_key=$apiKey&language=ar-SA", apiKey, autoTranslate) }
        val popularMovies = async { fetchTmdbList("https://api.themoviedb.org/3/movie/popular?api_key=$apiKey&language=ar-SA", apiKey, autoTranslate, MediaType.MOVIE) }
        val topTv = async { fetchTmdbList("https://api.themoviedb.org/3/tv/top_rated?api_key=$apiKey&language=ar-SA", apiKey, autoTranslate, MediaType.SERIES) }
        val netflix = async { fetchTmdbList("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=213&language=ar-SA", apiKey, autoTranslate, MediaType.SERIES) }
        val appleTv = async { fetchTmdbList("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=2552&language=ar-SA", apiKey, autoTranslate, MediaType.SERIES) }
        val anime = async { fetchTmdbList("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_genres=16&with_original_language=ja&sort_by=popularity.desc&language=ar-SA", apiKey, autoTranslate, MediaType.SERIES) }

        linkedMapOf(
            "الرائج اليوم" to trendingAll.await(),
            "أفلام شائعة" to popularMovies.await(),
            "مسلسلات الأعلى تقييماً" to topTv.await(),
            "أعمال Netflix الأصلية" to netflix.await(),
            "أعمال Apple TV+" to appleTv.await(),
            "أنمي رائج" to anime.await()
        )
    }

    suspend fun searchMulti(query: String, apiKey: String, autoTranslate: Boolean): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return fetchTmdbList(
            "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar-SA&query=$encoded&include_adult=false",
            apiKey,
            autoTranslate
        )
    }

    suspend fun discoverByPerson(personId: Int, apiKey: String, autoTranslate: Boolean): List<MediaItem> {
        val raw = HttpClientProvider.getText("https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=ar-SA") ?: return emptyList()
        val obj = JSONObject(raw)
        val castArr = obj.optJSONArray("cast") ?: return emptyList()
        val out = mutableListOf<MediaItem>()
        for (i in 0 until minOf(castArr.length(), 30)) {
            val item = castArr.getJSONObject(i)
            val mType = MediaType.fromString(item.optString("media_type", "movie"))
            parseTmdbSummaryItem(item, mType, autoTranslate)?.let { out.add(it) }
        }
        return out.distinctBy { it.id }
    }

    private suspend fun fetchTmdbList(
        url: String,
        apiKey: String,
        autoTranslate: Boolean,
        forcedType: MediaType? = null
    ): List<MediaItem> {
        val raw = HttpClientProvider.getText(url) ?: return emptyList()
        return try {
            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val mediaTypeStr = obj.optString("media_type", "")
                if (mediaTypeStr == "person") continue
                val type = forcedType ?: MediaType.fromString(mediaTypeStr)
                parseTmdbSummaryItem(obj, type, autoTranslate)?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun parseTmdbSummaryItem(obj: JSONObject, type: MediaType, autoTranslate: Boolean): MediaItem? {
        val tmdbId = obj.optInt("id", 0)
        if (tmdbId == 0) return null
        val arTitle = obj.optString("title").ifBlank { obj.optString("name") }
        val origTitle = obj.optString("original_title").ifBlank { obj.optString("original_name") }
        var overview = obj.optString("overview", "")
        if (overview.isNotBlank() && autoTranslate) {
            overview = ArabicAutoTranslator.translateToArabicIfNeeded(overview, true)
        }
        val posterPath = obj.optString("poster_path", "").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = obj.optString("backdrop_path", "").takeIf { it.isNotBlank() && it != "null" }
        val date = obj.optString("release_date").ifBlank { obj.optString("first_air_date") }
        val year = date.take(4)
        val vote = obj.optDouble("vote_average", 0.0)
        val tmdbScore = if (vote > 0.0) String.format("%.1f", vote) else null

        return MediaItem(
            id = "tmdb:$tmdbId",
            tmdbId = tmdbId,
            type = type,
            title = arTitle.ifBlank { origTitle },
            originalTitle = origTitle,
            arabicTitle = arTitle.ifBlank { origTitle },
            overview = overview,
            posterUrl = posterPath?.let { "$IMG_W500$it" },
            backdropUrl = backdropPath?.let { "$IMG_ORIGINAL$it" },
            year = year,
            ratings = RatingsBundle(tmdb = tmdbScore, imdb = tmdbScore)
        )
    }

    suspend fun getFullMediaDetails(
        item: MediaItem,
        tmdbKey: String,
        mdblistKey: String,
        autoTranslate: Boolean
    ): MediaItem = coroutineScope {
        val typePath = if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) "tv" else "movie"
        var resolvedTmdbId = item.tmdbId
        var resolvedImdbId = item.imdbId

        // Resolve TMDB ID from IMDb ID if needed
        if (resolvedTmdbId == null && !resolvedImdbId.isNullOrBlank()) {
            val findRaw = HttpClientProvider.getText("https://api.themoviedb.org/3/find/$resolvedImdbId?api_key=$tmdbKey&external_source=imdb_id")
            if (!findRaw.isNullOrBlank()) {
                val findObj = JSONObject(findRaw)
                val arr = if (typePath == "tv") findObj.optJSONArray("tv_results") else findObj.optJSONArray("movie_results")
                if (arr != null && arr.length() > 0) {
                    resolvedTmdbId = arr.getJSONObject(0).optInt("id")
                }
            }
        }

        if (resolvedTmdbId == null) return@coroutineScope item

        val arDetailDef = async {
            HttpClientProvider.getText("https://api.themoviedb.org/3/$typePath/$resolvedTmdbId?api_key=$tmdbKey&language=ar-SA&append_to_response=external_ids,credits,videos,images&include_image_language=ar,en,null")
        }
        val enDetailDef = async {
            HttpClientProvider.getText("https://api.themoviedb.org/3/$typePath/$resolvedTmdbId?api_key=$tmdbKey&language=en-US")
        }

        val arRaw = arDetailDef.await() ?: return@coroutineScope item
        val enRaw = enDetailDef.await()

        val arObj = JSONObject(arRaw)
        val enObj = enRaw?.let { runCatching { JSONObject(it) }.getOrNull() }

        val extIds = arObj.optJSONObject("external_ids")
        if (resolvedImdbId.isNullOrBlank()) {
            resolvedImdbId = extIds?.optString("imdb_id")?.takeIf { it.startsWith("tt") }
                ?: arObj.optString("imdb_id").takeIf { it.startsWith("tt") }
        }

        val arTitle = arObj.optString("title").ifBlank { arObj.optString("name") }
        val enTitle = enObj?.optString("title")?.ifBlank { enObj.optString("name") } ?: arTitle
        val origTitle = arObj.optString("original_title").ifBlank { arObj.optString("original_name") }

        var overview = arObj.optString("overview", "")
        if (overview.isBlank() && enObj != null) {
            overview = enObj.optString("overview", "")
        }
        if (overview.isNotBlank() && autoTranslate) {
            overview = ArabicAutoTranslator.translateToArabicIfNeeded(overview, true)
        }

        // Genres
        val genresList = mutableListOf<String>()
        val gArr = arObj.optJSONArray("genres")
        if (gArr != null) {
            for (i in 0 until gArr.length()) {
                genresList.add(gArr.getJSONObject(i).optString("name"))
            }
        }

        // Runtime
        val runtimeMin = if (typePath == "movie") {
            arObj.optInt("runtime", 0)
        } else {
            arObj.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 45
        }
        val runtimeStr = if (runtimeMin > 0) {
            val h = runtimeMin / 60
            val m = runtimeMin % 60
            if (h > 0) "${h}س ${m}د" else "${m} دقيقة"
        } else ""

        // ClearLogo from images
        var logoUrl: String? = null
        val logosArr = arObj.optJSONObject("images")?.optJSONArray("logos")
        if (logosArr != null && logosArr.length() > 0) {
            val path = logosArr.getJSONObject(0).optString("file_path")
            if (path.isNotBlank()) logoUrl = "$IMG_W500$path"
        }

        // Trailer YouTube Key
        var trailerKey: String? = null
        val videosArr = arObj.optJSONObject("videos")?.optJSONArray("results")
        if (videosArr != null) {
            for (i in 0 until videosArr.length()) {
                val v = videosArr.getJSONObject(i)
                if (v.optString("site") == "YouTube" && v.optString("type") == "Trailer") {
                    trailerKey = v.optString("key")
                    break
                }
            }
        }

        // Cast & Directors
        val castList = mutableListOf<CastMember>()
        val dirList = mutableListOf<CastMember>()
        val credits = arObj.optJSONObject("credits")
        val cArr = credits?.optJSONArray("cast")
        if (cArr != null) {
            for (i in 0 until minOf(cArr.length(), 18)) {
                val c = cArr.getJSONObject(i)
                val prof = c.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }
                castList.add(
                    CastMember(
                        id = c.optInt("id"),
                        name = c.optString("name"),
                        character = c.optString("character"),
                        profileUrl = prof?.let { "$IMG_W500$it" },
                        roleType = "ممثل"
                    )
                )
            }
        }
        val crewArr = credits?.optJSONArray("crew")
        if (crewArr != null) {
            for (i in 0 until crewArr.length()) {
                val cr = crewArr.getJSONObject(i)
                if (cr.optString("job") == "Director") {
                    val prof = cr.optString("profile_path").takeIf { it.isNotBlank() && it != "null" }
                    dirList.add(
                        CastMember(
                            id = cr.optInt("id"),
                            name = cr.optString("name"),
                            character = "مخرج",
                            profileUrl = prof?.let { "$IMG_W500$it" },
                            roleType = "مخرج"
                        )
                    )
                }
            }
        }

        // Networks
        val networksList = mutableListOf<String>()
        val netArr = arObj.optJSONArray("networks") ?: arObj.optJSONArray("production_companies")
        if (netArr != null) {
            for (i in 0 until minOf(netArr.length(), 4)) {
                networksList.add(netArr.getJSONObject(i).optString("name"))
            }
        }

        // Episodes (Season 1 by default for TV series)
        val episodesList = mutableListOf<EpisodeItem>()
        if (typePath == "tv") {
            val s1Raw = HttpClientProvider.getText("https://api.themoviedb.org/3/tv/$resolvedTmdbId/season/1?api_key=$tmdbKey&language=ar-SA")
            if (!s1Raw.isNullOrBlank()) {
                val epArr = JSONObject(s1Raw).optJSONArray("episodes")
                if (epArr != null) {
                    for (i in 0 until epArr.length()) {
                        val ep = epArr.getJSONObject(i)
                        val epNum = ep.optInt("episode_number", i + 1)
                        val still = ep.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
                        var epOver = ep.optString("overview", "")
                        if (epOver.isNotBlank() && autoTranslate) {
                            epOver = ArabicAutoTranslator.translateToArabicIfNeeded(epOver, true)
                        }
                        episodesList.add(
                            EpisodeItem(
                                id = "${resolvedImdbId ?: "tmdb:$resolvedTmdbId"}:1:$epNum",
                                season = 1,
                                episode = epNum,
                                title = ep.optString("name").ifBlank { "الحلقة $epNum" },
                                overview = epOver,
                                thumbnailUrl = still?.let { "$IMG_W500$it" },
                                airDate = ep.optString("air_date"),
                                runtimeMinutes = ep.optInt("runtime", 45)
                            )
                        )
                    }
                }
            }
        }

        // Multi-ratings via MDBList (IMDb, Rotten Tomatoes, Metacritic, Trakt, MAL)
        val vote = arObj.optDouble("vote_average", 0.0)
        val tmdbRating = if (vote > 0.0) String.format("%.1f", vote) else null
        var ratings = RatingsBundle(imdb = tmdbRating, tmdb = tmdbRating)
        if (!resolvedImdbId.isNullOrBlank() && mdblistKey.isNotBlank()) {
            val mdbRaw = HttpClientProvider.getText("https://mdblist.com/api/?apikey=$mdblistKey&i=$resolvedImdbId")
            if (!mdbRaw.isNullOrBlank()) {
                runCatching {
                    val mdbObj = JSONObject(mdbRaw)
                    val rArr = mdbObj.optJSONArray("ratings")
                    var imdbR = tmdbRating
                    var rtR: String? = null
                    var mcR: String? = null
                    var traktR: String? = null
                    var malR: String? = null
                    if (rArr != null) {
                        for (i in 0 until rArr.length()) {
                            val r = rArr.getJSONObject(i)
                            val src = r.optString("source")
                            val valStr = r.optString("value")
                            if (valStr.isNotBlank() && valStr != "0" && valStr != "null") {
                                when (src) {
                                    "imdb" -> imdbR = valStr
                                    "tomatoes" -> rtR = "$valStr%"
                                    "metacritic" -> mcR = valStr
                                    "trakt" -> traktR = "$valStr%"
                                    "myanimelist" -> malR = valStr
                                }
                            }
                        }
                    }
                    ratings = RatingsBundle(
                        imdb = imdbR,
                        tmdb = tmdbRating,
                        rottenTomatoes = rtR,
                        metacritic = mcR,
                        trakt = traktR,
                        mal = malR
                    )
                }
            }
        }

        val posterPath = arObj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
        val backdropPath = arObj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
        val date = arObj.optString("release_date").ifBlank { arObj.optString("first_air_date") }

        item.copy(
            id = resolvedImdbId ?: "tmdb:$resolvedTmdbId",
            imdbId = resolvedImdbId,
            tmdbId = resolvedTmdbId,
            title = arTitle.ifBlank { enTitle },
            arabicTitle = arTitle.ifBlank { enTitle },
            originalTitle = enTitle.ifBlank { origTitle },
            overview = overview,
            posterUrl = posterPath?.let { "$IMG_W500$it" } ?: item.posterUrl,
            backdropUrl = backdropPath?.let { "$IMG_ORIGINAL$it" } ?: item.backdropUrl,
            logoUrl = logoUrl,
            year = date.take(4).ifBlank { item.year },
            runtime = runtimeStr,
            genres = genresList,
            ratings = ratings,
            trailerYoutubeKey = trailerKey,
            episodes = episodesList,
            cast = castList,
            directors = dirList,
            networks = networksList
        )
    }

    suspend fun getSeasonEpisodes(
        tmdbId: Int,
        imdbId: String?,
        seasonNumber: Int,
        tmdbKey: String,
        autoTranslate: Boolean
    ): List<EpisodeItem> {
        val raw = HttpClientProvider.getText("https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbKey&language=ar-SA") ?: return emptyList()
        val epArr = JSONObject(raw).optJSONArray("episodes") ?: return emptyList()
        val list = mutableListOf<EpisodeItem>()
        for (i in 0 until epArr.length()) {
            val ep = epArr.getJSONObject(i)
            val epNum = ep.optInt("episode_number", i + 1)
            val still = ep.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
            var epOver = ep.optString("overview", "")
            if (epOver.isNotBlank() && autoTranslate) {
                epOver = ArabicAutoTranslator.translateToArabicIfNeeded(epOver, true)
            }
            list.add(
                EpisodeItem(
                    id = "${imdbId ?: "tmdb:$tmdbId"}:$seasonNumber:$epNum",
                    season = seasonNumber,
                    episode = epNum,
                    title = ep.optString("name").ifBlank { "الحلقة $epNum" },
                    overview = epOver,
                    thumbnailUrl = still?.let { "$IMG_W500$it" },
                    airDate = ep.optString("air_date"),
                    runtimeMinutes = ep.optInt("runtime", 45)
                )
            )
        }
        return list
    }

    suspend fun getAllArtworkOptions(item: MediaItem, tmdbKey: String): List<TmdbImageOption> {
        val tmdbId = item.tmdbId ?: return emptyList()
        val typePath = if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) "tv" else "movie"
        val raw = HttpClientProvider.getText("https://api.themoviedb.org/3/$typePath/$tmdbId/images?api_key=$tmdbKey") ?: return emptyList()
        val root = JSONObject(raw)
        val out = mutableListOf<TmdbImageOption>()

        val posters = root.optJSONArray("posters")
        if (posters != null) {
            for (i in 0 until minOf(posters.length(), 24)) {
                val p = posters.getJSONObject(i)
                val path = p.optString("file_path")
                if (path.isNotBlank()) {
                    out.add(TmdbImageOption("$IMG_W500$path", isBackdrop = false, lang = p.optString("iso_639_1", "en"), width = p.optInt("width"), height = p.optInt("height")))
                }
            }
        }
        val backdrops = root.optJSONArray("backdrops")
        if (backdrops != null) {
            for (i in 0 until minOf(backdrops.length(), 20)) {
                val b = backdrops.getJSONObject(i)
                val path = b.optString("file_path")
                if (path.isNotBlank()) {
                    out.add(TmdbImageOption("$IMG_ORIGINAL$path", isBackdrop = true, lang = b.optString("iso_639_1", "en"), width = b.optInt("width"), height = b.optInt("height")))
                }
            }
        }
        return out
    }
}
