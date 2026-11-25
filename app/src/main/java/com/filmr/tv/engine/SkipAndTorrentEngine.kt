package com.filmr.tv.engine
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class SkipSegment(val startMs: Long, val endMs: Long, val label: String)

object SkipAndTorrentEngine {
    suspend fun fetchSkipSegments(imdbId: String, season: Int, episode: Int, animeSkipClientId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SkipSegment>()
        if (imdbId.startsWith("tt")) {
            runCatching {
                val raw = (URL("https://api.introdb.app/v1/segments?imdb=$imdbId&season=$season&episode=$episode").openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 6000 }.inputStream.bufferedReader().use { it.readText() }
                JsonParser.parseString(raw).asJsonArray.forEach { el ->
                    val o = el.asJsonObject
                    val s = (o.get("start")?.asDouble ?: 0.0) * 1000
                    val e = (o.get("end")?.asDouble ?: 0.0) * 1000
                    if (e > s) list.add(SkipSegment(s.toLong(), e.toLong(), "تخطي المقدمة"))
                }
            }
        }
        if (animeSkipClientId.isNotBlank() && list.isEmpty()) {
            runCatching {
                val conn = (URL("https://api.anime-skip.com/graphql").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 6000
                    setRequestProperty("Content-Type", "application/json"); setRequestProperty("X-Client-ID", animeSkipClientId)
                }
                conn.outputStream.use { it.write("""{"query":"{ findShowsByExternalId(service: IMDB, serviceId: \"$imdbId\") { episodes { number timestamps { at type { name } } } } }"}""".toByteArray()) }
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val shows = JsonParser.parseString(res).asJsonObject.getAsJsonObject("data")?.getAsJsonArray("findShowsByExternalId")
                shows?.firstOrNull()?.asJsonObject?.getAsJsonArray("episodes")?.forEach { epEl ->
                    val epObj = epEl.asJsonObject
                    if (epObj.get("number")?.asString == episode.toString()) {
                        val ts = epObj.getAsJsonArray("timestamps")
                        if (ts != null && ts.size() >= 2) {
                            val s = ((ts[0].asJsonObject.get("at")?.asDouble ?: 0.0) * 1000).toLong()
                            val e = ((ts[1].asJsonObject.get("at")?.asDouble ?: 0.0) * 1000).toLong()
                            if (e > s) list.add(SkipSegment(s, e, "تخطي الشارة"))
                        }
                    }
                }
            }
        }
        list
    }

    fun resolveTorrentUrl(infoHash: String, fileIdx: Int?, torrentMode: String): String {
        if (infoHash.isBlank()) return ""
        val idxPath = fileIdx?.let { "/$it" } ?: ""
        return when (torrentMode) {
            "LocalServer" -> "http://127.0.0.1:11470/$infoHash$idxPath"
            else -> "https://webtor.io/#/show?magnet=magnet:?xt=urn:btih:$infoHash"
        }
    }
}
