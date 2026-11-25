package com.filmr.tv.engine

import com.filmr.tv.model.ArtworkOptions
import com.filmr.tv.model.MediaItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class TmdbArtworkEngine {
    private val gson = Gson()
    private val client = HttpClient(Android)
    private val imgBase = "https://image.tmdb.org/t/p/original"

    suspend fun fetchArtworkOptions(apiKey: String, imdbOrTmdbId: String, isSeries: Boolean): ArtworkOptions = runCatching {
        if (apiKey.isBlank()) return ArtworkOptions()
        val tmdbId = resolveTmdbId(apiKey, imdbOrTmdbId, isSeries) ?: return ArtworkOptions()
        val mediaType = if (isSeries) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$mediaType/$tmdbId/images?api_key=$apiKey&include_image_language=ar,en,null"
        val json = gson.fromJson(client.get(url).bodyAsText(), JsonObject::class.java)

        val posters = json.getAsJsonArray("posters")?.mapNotNull {
            it.asJsonObject.get("file_path")?.asString?.let { path -> "$imgBase$path" }
        } ?: emptyList()

        val backdrops = json.getAsJsonArray("backdrops")?.mapNotNull {
            it.asJsonObject.get("file_path")?.asString?.let { path -> "$imgBase$path" }
        } ?: emptyList()

        val logos = json.getAsJsonArray("logos")?.mapNotNull {
            it.asJsonObject.get("file_path")?.asString?.let { path -> "$imgBase$path" }
        } ?: emptyList()

        ArtworkOptions(posters = posters, backdrops = backdrops, logos = logos)
    }.getOrDefault(ArtworkOptions())

    suspend fun searchArabic(apiKey: String, query: String): List<MediaItem> = runCatching {
        if (apiKey.isBlank() || query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=ar-SA&query=$encoded"
        val results = gson.fromJson(client.get(url).bodyAsText(), JsonObject::class.java)
            .getAsJsonArray("results") ?: return emptyList()

        results.mapNotNull { el ->
            val obj = el.asJsonObject
            val mType = obj.get("media_type")?.asString ?: return@mapNotNull null
            if (mType != "movie" && mType != "tv") return@mapNotNull null
            val id = obj.get("id")?.asInt ?: return@mapNotNull null
            val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: return@mapNotNull null
            val poster = obj.get("poster_path")?.asString?.let { "$imgBase$it" } ?: ""
            val backdrop = obj.get("backdrop_path")?.asString?.let { "$imgBase$it" } ?: poster
            MediaItem(
                id = "tmdb:$id",
                tmdbId = id,
                type = if (mType == "tv") "series" else "movie",
                title = title,
                overview = obj.get("overview")?.asString ?: "",
                posterUrl = poster,
                backdropUrl = backdrop,
                rating = String.format("%.1f", obj.get("vote_average")?.asDouble ?: 8.0)
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun resolveTmdbId(apiKey: String, id: String, isSeries: Boolean): Int? {
        if (id.startsWith("tmdb:")) return id.removePrefix("tmdb:").toIntOrNull()
        if (id.startsWith("tt")) {
            val url = "https://api.themoviedb.org/3/find/$id?api_key=$apiKey&external_source=imdb_id"
            val json = gson.fromJson(client.get(url).bodyAsText(), JsonObject::class.java)
            val key = if (isSeries) "tv_results" else "movie_results"
            val arr = json.getAsJsonArray(key)
            if (arr != null && arr.size() > 0) {
                return arr[0].asJsonObject.get("id")?.asInt
            }
        }
        return id.toIntOrNull()
    }
}
