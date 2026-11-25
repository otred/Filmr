package com.filmr.tv.engine

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File
import java.nio.charset.Charset

object ArabicSubtitleSanitizer {
    private val client = HttpClient(Android)

    suspend fun downloadAndFixSubtitle(context: Context, subtitleUrl: String, id: String): File? = runCatching {
        val rawBytes = client.get(subtitleUrl).readBytes()
        val fixedText = decodeSmartArabic(rawBytes)
        val dir = File(context.cacheDir, "fixed_subs").apply { mkdirs() }
        val outFile = File(dir, "${id.hashCode()}.srt")
        outFile.writeText(fixedText, Charsets.UTF_8)
        outFile
    }.getOrNull()

    fun decodeSmartArabic(rawBytes: ByteArray): String {
        val utf8Attempt = rawBytes.toString(Charsets.UTF_8)
        return if (utf8Attempt.contains('\uFFFD')) {
            rawBytes.toString(Charset.forName("Windows-1256"))
        } else {
            utf8Attempt
        }
    }
}
