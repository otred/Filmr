package com.filmr.tv.data.torrent

import com.filmr.tv.data.remote.HttpClientProvider
import com.filmr.tv.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

sealed class TorrentResolveResult {
    data class Ready(val playableHttpUrl: String, val headers: Map<String, String> = emptyMap()) : TorrentResolveResult()
    data class Error(val messageAr: String) : TorrentResolveResult()
}

class TorrentStreamResolver {

    /**
     * Resolves a StreamSource (either direct HTTP/HLS or Torrent InfoHash/Magnet) into a playable HTTP stream URL.
     * Supports:
     * 1) Direct HTTP/HTTPS/M3U8/MPD streams immediately.
     * 2) Local/LAN TorrServer (127.0.0.1:8090 or custom IP) via REST API (/torrents + /stream).
     * 3) Automatic public WebTorrent HTTP gateway fallback so torrents play out-of-the-box even without local TorrServer!
     */
    suspend fun resolveToPlayableUrl(
        source: StreamSource,
        torrServerBaseUrl: String,
        onProgress: (String) -> Unit
    ): TorrentResolveResult = withContext(Dispatchers.IO) {
        // Case 1: Already a direct HTTP/HTTPS stream
        val rawUrl = source.url
        if (!source.isTorrent && !rawUrl.isNullOrBlank() && rawUrl.startsWith("http")) {
            return@withContext TorrentResolveResult.Ready(rawUrl, source.headers)
        }

        // Extract InfoHash or Magnet URI
        val infoHash = source.infoHash?.lowercase()?.trim()
            ?: extractInfoHashFromMagnet(rawUrl)
            ?: return@withContext TorrentResolveResult.Error("تعذر استخراج بصمة التورنت (InfoHash) من هذا الرابط.")

        val magnetLink = if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("magnet:")) {
            rawUrl
        } else {
            buildMagnetUri(infoHash, source.title)
        }

        val fileIdx = source.fileIdx ?: 0
        val cleanServer = torrServerBaseUrl.trim().trimEnd('/')

        // Case 2: Try TorrServer (local or configured on network)
        onProgress("جاري الاتصال بمحرك التورنت وتجهيز البث...")
        val torrServerStream = tryResolveViaTorrServer(cleanServer, magnetLink, infoHash, fileIdx)
        if (torrServerStream != null) {
            onProgress("تم تجهيز البث بنجاح، جاري التشغيل...")
            return@withContext TorrentResolveResult.Ready(torrServerStream)
        }

        // Case 3: Automatic WebTor / Public Torrent-to-HTTP Cloud Streaming Gateway Fallback
        onProgress("جاري تحويل رابط التورنت لبث مباشر عبر السحابة...")
        val cloudGatewayUrl = tryResolveViaWebtorGateway(infoHash, fileIdx)
        if (cloudGatewayUrl != null) {
            return@withContext TorrentResolveResult.Ready(cloudGatewayUrl)
        }

        TorrentResolveResult.Error(
            "تعذر الاتصال بخادم التورنت المحلي ($cleanServer). تأكد من تشغيل TorrServer أو اختر سيرفر بث مباشر (HTTP) من القائمة."
        )
    }

    private suspend fun tryResolveViaTorrServer(
        serverUrl: String,
        magnet: String,
        hash: String,
        fileIdx: Int
    ): String? {
        return try {
            // Check if TorrServer is alive
            val echo = HttpClientProvider.getText("$serverUrl/echo")
            if (echo == null) return null

            // Add torrent to TorrServer
            val addBody = JSONObject().apply {
                put("action", "add")
                put("link", magnet)
                put("title", "FilmrStream_$hash")
                put("save_to_db", false)
            }.toString()
            HttpClientProvider.postJson("$serverUrl/torrents", addBody)
            delay(800)

            val encodedLink = URLEncoder.encode(magnet, "UTF-8")
            "$serverUrl/stream/video.mkv?link=$encodedLink&index=$fileIdx&play"
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryResolveViaWebtorGateway(hash: String, fileIdx: Int): String? {
        return try {
            // Check Webtor public edge API availability
            val apiUrl = "https://api.webtor.io/subtitles/$hash"
            val ping = HttpClientProvider.getText(apiUrl)
            if (ping != null) {
                "https://stream.webtor.io/$hash/$fileIdx"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractInfoHashFromMagnet(magnet: String?): String? {
        if (magnet.isNullOrBlank()) return null
        val match = Regex("xt=urn:btih:([a-zA-Z0-9]{32,40})", RegexOption.IGNORE_CASE).find(magnet)
        return match?.groupValues?.getOrNull(1)?.lowercase()
    }

    private fun buildMagnetUri(hash: String, title: String): String {
        val dn = URLEncoder.encode(title.ifBlank { hash }, "UTF-8")
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.bittorrent.pw:1337/announce",
            "udp://public.popcorn-tracker.org:6969/announce"
        ).joinToString("") { "&tr=${URLEncoder.encode(it, "UTF-8")}" }
        return "magnet:?xt=urn:btih:$hash&dn=$dn$trackers"
    }
}
