package com.filmr.tv.engine
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object ArabicSubtitleSanitizer {
    suspend fun prepareSubtitle(context: Context, url: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            if (url.startsWith("file://")) return@runCatching Uri.parse(url)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000; setRequestProperty("User-Agent", "FilmrTV/2.0") }
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return@runCatching null
            val utf8 = String(bytes, Charsets.UTF_8)
            val decoded = if (utf8.contains('\uFFFD')) String(bytes, Charset.forName("windows-1256")) else utf8
            val cleaned = decoded.replace("\uFEFF", "").replace(Regex("<[^>]*>"), "").lines().joinToString("\n") { line ->
                val t = line.trim()
                if (t.any { it in '\u0600'..'\u06FF' }) "\u202B$t\u202C" else t
            }
            File(context.cacheDir, "sub_${url.hashCode()}.srt").apply { writeText(cleaned, Charsets.UTF_8) }.let { Uri.fromFile(it) }
        }.getOrNull()
    }
}
