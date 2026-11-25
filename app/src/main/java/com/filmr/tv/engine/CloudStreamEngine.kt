package com.filmr.tv.engine
import android.content.Context
import com.filmr.tv.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipFile

object CloudStreamEngine {
    suspend fun installFromUrl(context: Context, repoUrl: String): Result<List<AddonEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = repoUrl.trim()
            if (clean.endsWith(".cs3", true)) return@runCatching listOf(parseCs3File(context, clean))
            val raw = (URL(clean).openConnection() as HttpURLConnection).apply { connectTimeout = 9000; readTimeout = 12000 }.inputStream.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(raw)
            val list = mutableListOf<AddonEntity>()
            val pluginsUrl = if (json.isJsonObject) json.asJsonObject.getAsJsonArray("pluginLists")?.firstOrNull()?.asString ?: clean else clean
            val pluginsRaw = if (pluginsUrl != clean) (URL(pluginsUrl).openConnection() as HttpURLConnection).inputStream.bufferedReader().use { it.readText() } else raw
            val arr = JsonParser.parseString(pluginsRaw).asJsonArray
            arr.forEachIndexed { idx, el ->
                val p = el.asJsonObject
                val u = p.get("url")?.asString ?: return@forEachIndexed
                list.add(AddonEntity("cs:${p.get("internalName")?.asString ?: u.hashCode()}", p.get("name")?.asString ?: "CloudStream", u, p.get("description")?.takeIf { !it.isJsonNull }?.asString ?: "", p.get("iconUrl")?.takeIf { !it.isJsonNull }?.asString ?: "", "CloudStream", true, idx))
            }
            list
        }
    }

    private fun parseCs3File(context: Context, url: String): AddonEntity {
        val dir = File(context.filesDir, "cs3_plugins").apply { mkdirs() }
        val file = File(dir, "plugin_${url.hashCode()}.cs3")
        URL(url).openStream().use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
        var name = file.nameWithoutExtension
        runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("manifest.json")?.let { entry ->
                    val m = JsonParser.parseString(zip.getInputStream(entry).bufferedReader().use { it.readText() }).asJsonObject
                    name = m.get("name")?.asString ?: name
                }
            }
        }
        return AddonEntity("cs:${file.name}", name, file.absolutePath, "ملف إضافة CloudStream (.cs3)", "", "CloudStream", true, 0)
    }

    suspend fun fetchStreams(addons: List<AddonEntity>, title: String, season: Int = 1, episode: Int = 1): List<UnifiedStream> = coroutineScope {
        val q = URLEncoder.encode(title, "UTF-8")
        addons.filter { it.enabled && it.engineType == "CloudStream" && it.url.startsWith("http") }.map { addon ->
            async(Dispatchers.IO) {
                runCatching {
                    val base = addon.url.substringBeforeLast("/")
                    val raw = (URL("$base/search?q=$q&s=$season&e=$episode").openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 8000 }.inputStream.bufferedReader().use { it.readText() }
                    val arr = JsonParser.parseString(raw).asJsonArray
                    arr.mapIndexedNotNull { idx, el ->
                        val o = el.asJsonObject
                        val u = o.get("url")?.asString ?: return@mapIndexedNotNull null
                        UnifiedStream("${addon.id}_$idx", addon.name, o.get("title")?.asString ?: title, u, sourceName = addon.name, engineType = "CloudStream", quality = o.get("quality")?.asString ?: "1080p")
                    }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }
}
