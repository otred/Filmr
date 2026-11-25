package com.filmr.tv.engine

import android.content.Context
import com.filmr.tv.data.AddonEntity
import com.filmr.tv.model.UnifiedStream
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dalvik.system.DexClassLoader
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File

class CloudStreamEngine(private val context: Context) {
    private val gson = Gson()
    private val client = HttpClient(Android)
    private val pluginsDir = File(context.filesDir, "cs3_plugins").apply { mkdirs() }

    suspend fun parseRepository(repoUrl: String): Result<List<AddonEntity>> = runCatching {
        val repoText = client.get(repoUrl.trim()).bodyAsText()
        val repoJson = gson.fromJson(repoText, JsonObject::class.java)
        val pluginLists = repoJson.getAsJsonArray("pluginLists") ?: return@runCatching emptyList<UnifiedStream>()

        val resultAddons = mutableListOf<AddonEntity>()
        for (listEl in pluginLists) {
            val listUrl = listEl.asString
            val pluginsText = client.get(listUrl).bodyAsText()
            val pluginsArray = gson.fromJson(pluginsText, JsonArray::class.java)
            pluginsArray?.forEach { pEl ->
                val p = pEl.asJsonObject
                val cs3Url = p.get("url")?.asString ?: return@forEach
                val name = p.get("name")?.asString ?: "CloudStream Plugin"
                val desc = p.get("description")?.asString ?: "إضافة كلاود ستريم (.cs3)"
                val icon = p.get("iconUrl")?.asString
                resultAddons.add(
                    AddonEntity(
                        url = cs3Url,
                        name = name,
                        description = desc,
                        iconUrl = icon,
                        type = "CLOUDSTREAM",
                        isEnabled = true
                    )
                )
            }
        }
        resultAddons
    }

    suspend fun downloadAndLoadPlugin(addon: AddonEntity): DexClassLoader? = runCatching {
        val fileName = "${addon.name.hashCode()}.cs3"
        val targetFile = File(pluginsDir, fileName)
        if (!targetFile.exists()) {
            val bytes = client.get(addon.url).readBytes()
            targetFile.writeBytes(bytes)
        }
        targetFile.setReadOnly()
        val optDir = File(context.cacheDir, "cs3_dex_opt").apply { mkdirs() }
        DexClassLoader(
            targetFile.absolutePath,
            optDir.absolutePath,
            null,
            context.classLoader
        )
    }.getOrNull()

    suspend fun fetchStreamsFromPlugin(
        addon: AddonEntity,
        queryTitle: String,
        season: Int?,
        episode: Int?
    ): List<UnifiedStream> = runCatching {
        val loader = downloadAndLoadPlugin(addon) ?: return emptyList<UnifiedStream>()
        val manifestEntry = runCatching { loader.loadClass("com.lagradost.cloudstream3.plugins.Plugin") }.getOrNull()
        if (manifestEntry != null) {
            emptyList<UnifiedStream>()
        } else {
            emptyList<UnifiedStream>()
        }
    }.getOrDefault(emptyList<UnifiedStream>())
}
