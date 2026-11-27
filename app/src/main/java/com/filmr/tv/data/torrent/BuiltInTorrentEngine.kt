package com.filmr.tv.data.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

data class TorrentStreamStatus(
    val isActive: Boolean = false,
    val statusText: String = "",
    val progressPercent: Int = 0,
    val downloadSpeedKb: Int = 0,
    val peersCount: Int = 0,
    val streamUrl: String? = null,
    val fileName: String = "",
    val error: String? = null
)

class BuiltInTorrentEngine(private val context: Context) {

    private val _status = MutableStateFlow(TorrentStreamStatus())
    val status: StateFlow<TorrentStreamStatus> = _status.asStateFlow()

    private var sessionManager: SessionManager? = null
    private var currentHandle: TorrentHandle? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val torrentCacheDir: File by lazy {
        File(context.cacheDir, "filmr_torrent_stream").apply { mkdirs() }
    }

    @Synchronized
    private fun ensureSession(lowEndMode: Boolean = false): SessionManager {
        sessionManager?.let {
            if (it.isRunning) return it
        }
        val settings = SettingsPack()
        settings.setString(
            settings_pack.string_types.user_agent.swigValue(),
            "FilmrTV/2.0 libtorrent4j"
        )
        val maxConns = if (lowEndMode) 65 else 150
        settings.setInteger(settings_pack.int_types.connections_limit.swigValue(), maxConns)
        settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 2)
        settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)

        val sm = SessionManager()
        sm.start(SessionParams(settings))
        sessionManager = sm
        return sm
    }

    fun startMagnetStream(
        magnetUri: String,
        preferredFileIdx: Int? = null,
        lowEndMode: Boolean = false
    ) {
        stopCurrentStream(keepSession = true)

        _status.value = TorrentStreamStatus(
            isActive = true,
            statusText = "جاري الاتصال بشبكة التورنت وجلب البيانات..."
        )

        monitorJob = scope.launch {
            try {
                val sm = ensureSession(lowEndMode)
                val enrichedMagnet = appendPublicTrackers(magnetUri)
                val sha1 = extractInfoHashSha1(magnetUri)

                val infoBytes = withTimeoutOrNull(25_000L) {
                    sm.fetchMagnet(enrichedMagnet, 25, torrentCacheDir)
                }

                var targetSha1 = sha1
                if (infoBytes != null) {
                    val ti = TorrentInfo(infoBytes)
                    targetSha1 = ti.infoHash()
                    sm.download(ti, torrentCacheDir)
                } else {
                    sm.download(enrichedMagnet, torrentCacheDir)
                }

                var handle: TorrentHandle? = null
                if (targetSha1 != null) {
                    for (i in 0..35) {
                        handle = sm.find(targetSha1)
                        if (handle != null && handle.isValid) break
                        delay(350L)
                    }
                }

                if (handle == null || !handle.isValid) {
                    _status.value = TorrentStreamStatus(
                        isActive = false,
                        error = "تعذر العثور على مصادر نشطة لهذا التورنت"
                    )
                    return@launch
                }

                currentHandle = handle
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                var torrentInfo: TorrentInfo? = handle.torrentFile()
                var metaWait = 0
                while ((torrentInfo == null || !torrentInfo.isValid) && metaWait < 60 && isActive) {
                    val st = handle.status()
                    _status.value = _status.value.copy(
                        statusText = "جاري جلب معلومات الملفات (${st.numPeers()} متصل)...",
                        peersCount = st.numPeers()
                    )
                    delay(500L)
                    torrentInfo = handle.torrentFile()
                    metaWait++
                }

                if (torrentInfo == null || !torrentInfo.isValid) {
                    _status.value = TorrentStreamStatus(
                        isActive = false,
                        error = "انتهت مهلة جلب بيانات التورنت (لا يوجد سيدرز كافٍ)"
                    )
                    return@launch
                }

                val numFiles = torrentInfo.numFiles()
                val files = torrentInfo.files()
                val videoExts = listOf(".mkv", ".mp4", ".avi", ".webm", ".mov", ".m4v", ".ts")

                var targetIdx = preferredFileIdx?.takeIf { it in 0 until numFiles } ?: -1
                if (targetIdx == -1) {
                    var maxSize = -1L
                    for (idx in 0 until numFiles) {
                        val path = files.filePath(idx).lowercase()
                        val size = files.fileSize(idx)
                        if (videoExts.any { path.endsWith(it) } && size > maxSize) {
                            maxSize = size
                            targetIdx = idx
                        }
                    }
                }
                if (targetIdx == -1 && numFiles > 0) {
                    targetIdx = (0 until numFiles).maxByOrNull { files.fileSize(it) } ?: 0
                }

                val priorities = Array(numFiles) { idx ->
                    if (idx == targetIdx) Priority.TOP_PRIORITY else Priority.IGNORE
                }
                handle.prioritizeFiles(priorities)

                val firstPiece = torrentInfo.mapFile(targetIdx, 0L, 1).piece()
                val fileSize = files.fileSize(targetIdx)
                val lastPiece = torrentInfo.mapFile(targetIdx, (fileSize - 1024L).coerceAtLeast(0L), 1).piece()
                val totalPieces = torrentInfo.numPieces()

                for (p in firstPiece..(firstPiece + 12).coerceAtMost(totalPieces - 1)) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(p, (p - firstPiece) * 150)
                }
                for (p in (lastPiece - 3).coerceAtLeast(firstPiece)..lastPiece) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(p, 500)
                }

                val targetFile = File(torrentCacheDir, files.filePath(targetIdx))
                val fileName = targetFile.name

                val localUrl = startHttpStreamingServer(handle, torrentInfo, targetIdx, targetFile, fileSize)

                val minInitialBytes = (2.5 * 1024 * 1024).toLong().coerceAtMost(fileSize / 10 + 512_000L)
                var readyNotified = false

                while (isActive && handle.isValid) {
                    val st = handle.status()
                    val downloaded = st.totalWantedDone()
                    val speedKb = st.downloadRate() / 1024
                    val peers = st.numPeers()
                    val pct = if (fileSize > 0) ((downloaded * 100) / fileSize).toInt().coerceIn(0, 100) else 0

                    if (!readyNotified && (downloaded >= minInitialBytes || pct >= 2)) {
                        readyNotified = true
                        _status.value = TorrentStreamStatus(
                            isActive = true,
                            statusText = "جاهز للتشغيل ($speedKb KB/s • $peers متصل)",
                            progressPercent = pct,
                            downloadSpeedKb = speedKb,
                            peersCount = peers,
                            streamUrl = localUrl,
                            fileName = fileName
                        )
                    } else if (!readyNotified) {
                        val mbDone = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                        _status.value = TorrentStreamStatus(
                            isActive = true,
                            statusText = "تجهيز البث: $mbDone MB • السرعة $speedKb KB/s • متصلون: $peers",
                            progressPercent = pct,
                            downloadSpeedKb = speedKb,
                            peersCount = peers,
                            fileName = fileName
                        )
                    } else {
                        _status.value = _status.value.copy(
                            statusText = "يبث مباشرة • $speedKb KB/s • متصلون: $peers",
                            progressPercent = pct,
                            downloadSpeedKb = speedKb,
                            peersCount = peers
                        )
                    }
                    delay(800L)
                }
            } catch (_: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e("FilmrTorrent", "Torrent error", e)
                _status.value = TorrentStreamStatus(
                    isActive = false,
                    error = "خطأ في محرك التورنت: ${e.localizedMessage ?: "خطأ غير معروف"}"
                )
            }
        }
    }

    private fun startHttpStreamingServer(
        handle: TorrentHandle,
        torrentInfo: TorrentInfo,
        fileIdx: Int,
        targetFile: File,
        fileSize: Long
    ): String {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = server
        val port = server.localPort

        serverJob = scope.launch {
            while (isActive && !server.isClosed) {
                try {
                    val client = server.accept()
                    launch {
                        handleHttpClient(client, handle, torrentInfo, fileIdx, targetFile, fileSize)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return "http://127.0.0.1:$port/video.mkv"
    }

    private suspend fun handleHttpClient(
        socket: Socket,
        handle: TorrentHandle,
        torrentInfo: TorrentInfo,
        fileIdx: Int,
        targetFile: File,
        fileSize: Long
    ) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 30_000
            val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return@withContext
            var rangeHeader: String? = null
            var line: String?
            while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.lowercase().startsWith("range:")) {
                    rangeHeader = line!!.substringAfter(":").trim()
                }
            }

            var startByte = 0L
            var endByte = fileSize - 1L
            var isPartial = false

            if (rangeHeader != null && rangeHeader!!.startsWith("bytes=")) {
                isPartial = true
                val rangeSpec = rangeHeader!!.removePrefix("bytes=").split("-")
                startByte = rangeSpec[0].toLongOrNull() ?: 0L
                if (rangeSpec.size > 1 && rangeSpec[1].isNotEmpty()) {
                    endByte = rangeSpec[1].toLongOrNull()?.coerceAtMost(fileSize - 1L) ?: (fileSize - 1L)
                }
            }

            val contentLength = (endByte - startByte + 1L).coerceAtLeast(0L)
            val mimeType = when {
                targetFile.name.endsWith(".mp4", true) -> "video/mp4"
                targetFile.name.endsWith(".webm", true) -> "video/webm"
                else -> "video/x-matroska"
            }

            val headers = buildString {
                if (isPartial) {
                    append("HTTP/1.1 206 Partial Content
")
                    append("Content-Range: bytes $startByte-$endByte/$fileSize
")
                } else {
                    append("HTTP/1.1 200 OK
")
                }
                append("Content-Type: $mimeType
")
                append("Accept-Ranges: bytes
")
                append("Content-Length: $contentLength
")
                append("Connection: close

")
            }
            output.write(headers.toByteArray(StandardCharsets.UTF_8))
            output.flush()

            if (requestLine.startsWith("HEAD")) return@withContext

            val reqPiece = torrentInfo.mapFile(fileIdx, startByte, 1).piece()
            val totalPieces = torrentInfo.numPieces()
            for (p in reqPiece..(reqPiece + 10).coerceAtMost(totalPieces - 1)) {
                if (handle.isValid && !handle.havePiece(p)) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(p, (p - reqPiece) * 100)
                }
            }

            var waitCount = 0
            while (!targetFile.exists() && waitCount < 40 && !socket.isClosed) {
                delay(250L)
                waitCount++
            }
            if (!targetFile.exists()) return@withContext

            RandomAccessFile(targetFile, "r").use { raf ->
                var currentPos = startByte
                val buffer = ByteArray(64 * 1024)

                while (currentPos <= endByte && !socket.isClosed && handle.isValid) {
                    val piece = torrentInfo.mapFile(fileIdx, currentPos, 1).piece()
                    var pieceWait = 0
                    while (!handle.havePiece(piece) && pieceWait < 120 && !socket.isClosed && handle.isValid) {
                        handle.piecePriority(piece, Priority.TOP_PRIORITY)
                        handle.setPieceDeadline(piece, 50)
                        delay(200L)
                        pieceWait++
                    }
                    if (!handle.havePiece(piece)) break

                    raf.seek(currentPos)
                    val toRead = minOf(buffer.size.toLong(), endByte - currentPos + 1L).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) {
                        delay(100L)
                        continue
                    }
                    output.write(buffer, 0, read)
                    currentPos += read
                }
                output.flush()
            }
        } catch (_: Exception) {
            // Client disconnected or seeked to another offset
        } finally {
            runCatching { socket.close() }
        }
    }

    fun stopCurrentStream(keepSession: Boolean = false) {
        monitorJob?.cancel()
        serverJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null

        currentHandle?.let { h ->
            runCatching {
                if (h.isValid) {
                    sessionManager?.remove(h)
                }
            }
        }
        currentHandle = null
        _status.value = TorrentStreamStatus(isActive = false)

        if (!keepSession) {
            runCatching {
                sessionManager?.stop()
                sessionManager = null
            }
            runCatching {
                torrentCacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
    }

    private fun extractInfoHashSha1(magnet: String): Sha1Hash? {
        return runCatching {
            val hexMatch = Regex("btih:([a-fA-F0-9]{40})").find(magnet)?.groupValues?.get(1)
            if (hexMatch != null) return Sha1Hash(hexMatch)

            val b32Match = Regex("btih:([a-zA-Z2-7]{32})").find(magnet)?.groupValues?.get(1)
            if (b32Match != null) {
                val hex = base32ToHex(b32Match.uppercase())
                if (hex.length == 40) return Sha1Hash(hex)
            }
            null
        }.getOrNull()
    }

    private fun base32ToHex(base32: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val out = StringBuilder()
        for (ch in base32) {
            val valIdx = alphabet.indexOf(ch)
            if (valIdx < 0) continue
            buffer = (buffer shl 5) or valIdx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                val byteVal = (buffer shr (bitsLeft - 8)) and 0xFF
                out.append(String.format("%02x", byteVal))
                bitsLeft -= 8
            }
        }
        return out.toString()
    }

    private fun appendPublicTrackers(magnet: String): String {
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://public.popcorn-tracker.org:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://exodus.desync.com:6969/announce"
        )
        val sb = StringBuilder(magnet)
        for (tr in trackers) {
            if (!magnet.contains(tr)) {
                sb.append("&tr=").append(java.net.URLEncoder.encode(tr, "UTF-8"))
            }
        }
        return sb.toString()
    }
}
