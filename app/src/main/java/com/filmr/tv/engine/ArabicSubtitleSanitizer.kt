package com.filmr.tv.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ArabicSubtitleSanitizer {
    suspend fun prepareSubtitle(context: Context, subtitleUrl: String, delayMs: Long = 0L): Uri? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(subtitleUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val cleaned = cleanAndShift(raw, delayMs)
            val file = File(context.cacheDir, "sub_${subtitleUrl.hashCode()}_$delayMs.srt")
            file.writeText(cleaned, Charsets.UTF_8)
            Uri.fromFile(file)
        } catch (_: Exception) { null }
    }

    private fun cleanAndShift(raw: String, delayMs: Long): String {
        val noBom = raw.removePrefix("\uFEFF").replace("WEBVTT", "").trim()
        if (delayMs == 0L) return noBom
        val regex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        return regex.replace(noBom) { m ->
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4]) + delayMs
            val end = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8]) + delayMs
            "${fromMs(start.coerceAtLeast(0L))} --> ${fromMs(end.coerceAtLeast(0L))}"
        }
    }

    private fun toMs(h: String, m: String, s: String, ms: String): Long =
        h.toLong() * 3600000L + m.toLong() * 60000L + s.toLong() * 1000L + ms.toLong()

    private fun fromMs(totalMs: Long): String {
        val h = totalMs / 3600000L
        val m = (totalMs % 3600000L) / 60000L
        val s = (totalMs % 60000L) / 1000L
        val ms = totalMs % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }
}
