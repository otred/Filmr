package com.filmr.tv.engine
import com.filmr.tv.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object StremioEngine {
    private fun postJson(u: String, body: String): String = (URL(u).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; doOutput = true; connectTimeout = 10000; readTimeout = 12000; setRequestProperty("Content-Type", "application/json") }.also { it.outputStream.use { o -> o.write(body.toByteArray(Charsets.UTF_8)) } }.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun httpGet(u: String): String = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 9000; readTimeout = 12000 }.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    suspend fun loginAndSync(email: String, password: String): Result<Pair<String, List<AddonEntity>>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = postJson("https://api.strem.io/api/login", """{"type":"Login","email":"${email.trim()}","password":"$password","facebook":false}""")
            val authKey = JsonParser.parseString(res).asJsonObject.getAsJsonObject("result")?.get("authKey")?.asString ?: throw Exception("فشل تسجيل الدخول")
            Pair(authKey, fetchUserAddons(authKey))
        }
    }

    suspend fun fetchUserAddons(authKey: String): List<AddonEntity> = withContext(Dispatchers.IO) {
        if (authKey.isBlank()) return@withContext emptyList()
        runCatching {
            val res = postJson("https://api.strem.io/api/addonCollectionGet", """{"type":"AddonCollectionGet","authKey":"$authKey","update":true}""")
            val arr = JsonParser.parseString(res).asJsonObject.getAsJsonObject("result")?.getAsJsonArray("addons") ?: return@runCatching emptyList()
            arr.mapIndexedNotNull { idx, el ->
                val item = el.asJsonObject
                val u = item.get("transportUrl")?.asString ?: return@mapIndexedNotNull null
                val m = item.getAsJsonObject("manifest")
                AddonEntity("stremio:${m?.get("id")?.asString ?: u.hashCode()}", m?.get("name")?.asString ?: "Stremio", u, m?.get("description")?.takeIf { !it.isJsonNull }?.asString ?: "", m?.get("logo")?.takeIf { !it.isJsonNull }?.asString ?: "", "Stremio", true, idx)
            }
        }.getOrDefault(emptyList())
    }
    suspend fun fetchUserLibrary(authKey: String): List<LibraryEntity> = withContext(Dispatchers.IO) {
        if (authKey.isBlank()) return@withContext emptyList()
        runCatching {
            val res = postJson("https://api.strem.io/api/datastoreGet", """{"type":"DatastoreGet","authKey":"$authKey","collection":"libraryItem","all":true}""")
            val arr = JsonParser.parseString(res).asJsonObject.getAsJsonArray("result") ?: return@runCatching emptyList()
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("_id")?.asString ?: return@mapNotNull null
                if (!id.startsWith("tt")) return@mapNotNull null
                LibraryEntity(id, id, 0, obj.get("type")?.asString ?: "movie", obj.get("name")?.asString ?: id, obj.get("name")?.asString ?: id, obj.get("poster")?.takeIf { !it.isJsonNull }?.asString ?: "", "", 0.0, obj.get("year")?.takeIf { !it.isJsonNull }?.asString ?: "")
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchManifest(manifestUrl: String): Result<AddonEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = if (manifestUrl.endsWith("/manifest.json")) manifestUrl else "${manifestUrl.trimEnd('/')}/manifest.json"
            val m = JsonParser.parseString(httpGet(clean)).asJsonObject
            AddonEntity("stremio:${m.get("id")?.asString ?: clean.hashCode()}", m.get("name")?.asString ?: "إضافة Stremio", clean, m.get("description")?.takeIf { !it.isJsonNull }?.asString ?: "", m.get("logo")?.takeIf { !it.isJsonNull }?.asString ?: "", "Stremio", true, 0)
        }
    }
    suspend fun fetchAllStreams(addons: List<AddonEntity>, type: String, mediaId: String, season: Int = 1, episode: Int = 1): List<UnifiedStream> = coroutineScope {
        val targetId = if (type == "series") "$mediaId:$season:$episode" else mediaId
        val encodedId = URLEncoder.encode(targetId, "UTF-8")
        addons.filter { it.enabled && it.engineType == "Stremio" }.map { addon ->
            async(Dispatchers.IO) {
                runCatching {
                    val base = addon.url.removeSuffix("/manifest.json").trimEnd('/')
                    val arr = JsonParser.parseString(httpGet("$base/stream/$type/$encodedId.json")).asJsonObject.getAsJsonArray("streams") ?: return@runCatching emptyList()
                    arr.mapIndexedNotNull { i, el ->
                        val s = el.asJsonObject
                        val url = s.get("url")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val hash = s.get("infoHash")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        if (url.isBlank() && hash.isBlank()) return@mapIndexedNotNull null
                        val title = s.get("title")?.takeIf { !it.isJsonNull }?.asString ?: s.get("description")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val name = s.get("name")?.takeIf { !it.isJsonNull }?.asString ?: addon.name
                        val quality = Regex("(4K|2160p|1080p|720p|480p|HDR|DV)", RegexOption.IGNORE_CASE).find("$name $title")?.value?.uppercase() ?: "HD"
                        val size = Regex("(\\d+(?:\\.\\d+)?\\s*(?:GB|MB))", RegexOption.IGNORE_CASE).find(title)?.value ?: ""
                        val seeders = Regex("👤\\s*(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val subs = s.getAsJsonArray("subtitles")?.mapIndexedNotNull { idx, subEl ->
                            val so = subEl.asJsonObject
                            val su = so.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@mapIndexedNotNull null
                            SubtitleTrack("sub_${addon.id}_$idx", su, so.get("lang")?.asString ?: "ar", so.get("label")?.asString ?: so.get("lang")?.asString ?: "ترجمة")
                        } ?: emptyList()
                        UnifiedStream("${addon.id}_$i", name, title, url, hash, s.get("fileIdx")?.takeIf { !it.isJsonNull }?.asInt, addon.name, "Stremio", quality, size, seeders, subs)
                    }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }
}
