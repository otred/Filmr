package com.filmr.tv.data.torrent

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

data class TorrentEngineState(
    val isActive: Boolean = false,
    val statusText: String = "",
    val downloadSpeedKbps: Int = 0,
    val peers: Int = 0,
    val progressPercent: Int = 0,
    val localStreamUrl: String? = null,
    val error: String? = null
)

object BuiltInTorrentEngine {
    private var session: SessionManager? = null
    private var activeHandle: TorrentHandle? = null
    private var proxySocket: ServerSocket? = null
    private var engineJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TorrentEngineState())
    val state: StateFlow<TorrentEngineState> = _state.asStateFlow()

    @Synchronized
    private fun ensureSession(): SessionManager {
        val existing = session
        if (existing != null && existing.isRunning) return existing
        return SessionManager().apply {
            start()
            session = this
        }
    }

    fun startMagnet(context: Context, magnetUri: String) {
        stopCurrent()
        _state.value = TorrentEngineState(
            isActive = true,
            statusText = "جاري الاتصال بشبكة التورنت وجلب البيانات..."
        )

        engineJob = scope.launch {
            try {
                val sm = ensureSession()
                val cacheDir = File(context.cacheDir, "filmr_torrents").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }

                val metaBytes = sm.fetchMagnet(magnetUri, 35, cacheDir)
                if (metaBytes == null || metaBytes.isEmpty()) {
                    _state.value = TorrentEngineState(
                        isActive = false,
                        error = "تعذر جلب بيانات التورنت (لا يوجد سيدرز نشطون حالياً)"
                    )
                    return@launch
                }

                val ti = TorrentInfo.bdecode(metaBytes)
                val numFiles = ti.numFiles()
                val files = ti.files()
                var bestIndex = 0
                var bestSize = -1L
                for (i in 0 until numFiles) {
                    val name = files.fileName(i).lowercase()
                    val sz = files.fileSize(i)
                    val isVideo = name.endsWith(".mkv") || name.endsWith(".mp4") ||
                        name.endsWith(".avi") || name.endsWith(".webm") || name.endsWith(".mov")
                    if (isVideo && sz > bestSize) {
                        bestSize = sz
                        bestIndex = i
                    }
                }
                if (bestSize <= 0L && numFiles > 0) {
                    bestIndex = 0
                    bestSize = files.fileSize(0)
                }

                sm.download(ti, cacheDir)
                delay(400)
                val handle = sm.find(ti.infoHash())
                if (handle == null) {
                    _state.value = TorrentEngineState(isActive = false, error = "فشل بدء تحميل التورنت")
                    return@launch
                }
                activeHandle = handle
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                val filePrios = Array(numFiles) { idx ->
                    if (idx == bestIndex) Priority.TOP_PRIORITY else Priority.IGNORE
                }
                handle.prioritizeFiles(filePrios)

                val firstPiece = ti.mapFile(bestIndex, 0, 1).piece()
                val lastPiece = ti.mapFile(bestIndex, (bestSize - 1).coerceAtLeast(0), 1).piece()
                for (p in firstPiece..(firstPiece + 18).coerceAtMost(lastPiece)) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                }
                for (p in (lastPiece - 4).coerceAtLeast(firstPiece)..lastPiece) {
                    handle.piecePriority(p, Priority.TOP_PRIORITY)
                }

                val targetFile = File(cacheDir, files.filePath(bestIndex))
                val minReadyBytes = (6L * 1024L * 1024L).coerceAtMost(bestSize / 4)

                var waitedMs = 0
                while (isActive && waitedMs < 45_000) {
                    val st = handle.status()
                    val speedKb = st.downloadPayloadRate() / 1024
                    val peers = st.numPeers()
                    val pct = (st.progress() * 100).toInt()
                    val currentLen = if (targetFile.exists()) targetFile.length() else 0L

                    _state.value = _state.value.copy(
                        statusText = "جاري تجهيز البث المباشر... ($speedKb KB/s • $peers متصل)",
                        downloadSpeedKbps = speedKb,
                        peers = peers,
                        progressPercent = pct
                    )

                    if (currentLen >= minReadyBytes && handle.havePiece(firstPiece)) {
                        break
                    }
                    delay(500)
                    waitedMs += 500
                }

                val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
                proxySocket = server
                val port = server.localPort
                val ext = targetFile.extension.ifBlank { "mkv" }
                val streamUrl = "http://127.0.0.1:$port/stream.$ext"

                _state.value = _state.value.copy(
                    statusText = "جاهز للتشغيل",
                    localStreamUrl = streamUrl
                )

                launch {
                    while (isActive && !server.isClosed) {
                        val client = try { server.accept() } catch (_: Exception) { break }
                        launch { serveClient(client, targetFile, bestSize, handle, ti, bestIndex) }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = TorrentEngineState(
                        isActive = false,
                        error = "خطأ في محرك التورنت: ${e.message ?: "غير معروف"}"
                    )
                }
            }
        }
    }

    private suspend fun serveClient(
        socket: Socket,
        file: File,
        totalSize: Long,
        handle: TorrentHandle,
        ti: TorrentInfo,
        fileIndex: Int
    ) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val input = s.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@use
                var rangeHeader: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }

                var start = 0L
                var end = totalSize - 1
                var isPartial = false
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    isPartial = true
                    val parts = rangeHeader.removePrefix("bytes=").split("-")
                    start = parts[0].toLongOrNull() ?: 0L
                    if (parts.size > 1 && parts[1].isNotBlank()) {
                        end = parts[1].toLongOrNull() ?: (totalSize - 1)
                    }
                }
                if (start >= totalSize) start = 0L
                if (end >= totalSize) end = totalSize - 1

                val reqPiece = ti.mapFile(fileIndex, start, 1).piece()
                val maxPiece = ti.mapFile(fileIndex, (totalSize - 1).coerceAtLeast(0), 1).piece()
                for (p in reqPiece..(reqPiece + 12).coerceAtMost(maxPiece)) {
                    try { handle.piecePriority(p, Priority.TOP_PRIORITY) } catch (_: Exception) {}
                }

                val out = BufferedOutputStream(s.getOutputStream())
                val contentLength = (end - start + 1).coerceAtLeast(0)
                val mime = if (file.name.endsWith(".mp4", true)) "video/mp4" else "video/x-matroska"

                if (isPartial) {
                    out.write("HTTP/1.1 206 Partial Content
".toByteArray())
                    out.write("Content-Range: bytes $start-$end/$totalSize
".toByteArray())
                } else {
                    out.write("HTTP/1.1 200 OK
".toByteArray())
                }
                out.write("Content-Type: $mime
".toByteArray())
                out.write("Content-Length: $contentLength
".toByteArray())
                out.write("Accept-Ranges: bytes
".toByteArray())
                out.write("Connection: close

".toByteArray())
                out.flush()

                if (requestLine.startsWith("HEAD")) return@use

                var waitCount = 0
                while ((!file.exists() || !handle.havePiece(reqPiece)) && waitCount < 80) {
                    delay(200)
                    waitCount++
                }
                if (!file.exists()) return@use

                RandomAccessFile(file, "r").use { raf ->
                    var pos = start
                    val buf = ByteArray(64 * 1024)
                    while (pos <= end) {
                        val currentPiece = ti.mapFile(fileIndex, pos, 1).piece()
                        var pieceWait = 0
                        while (!handle.havePiece(currentPiece) && pieceWait < 60) {
                            try { handle.piecePriority(currentPiece, Priority.TOP_PRIORITY) } catch (_: Exception) {}
                            delay(150)
                            pieceWait++
                        }
                        if (pos >= raf.length()) {
                            delay(200)
                            if (pos >= raf.length()) break
                        }
                        raf.seek(pos)
                        val toRead = minOf(buf.size.toLong(), end - pos + 1, raf.length() - pos).toInt()
                        if (toRead <= 0) break
                        val read = raf.read(buf, 0, toRead)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        pos += read
                    }
                    out.flush()
                }
            }
        } catch (_: Exception) {}
    }

    fun stopCurrent() {
        engineJob?.cancel()
        try { proxySocket?.close() } catch (_: Exception) {}
        proxySocket = null
        try {
            activeHandle?.let { h ->
                session?.remove(h)
            }
        } catch (_: Exception) {}
        activeHandle = null
        _state.value = TorrentEngineState()
    }
}
