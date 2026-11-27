package com.filmr.tv.data.torrent

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket

data class TorrentStreamState(
    val statusText: String = "",
    val progressPercent: Int = 0,
    val downloadSpeedKb: Int = 0,
    val peers: Int = 0,
    val localStreamUrl: String? = null,
    val error: String? = null
)

class BuiltInTorrentEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: SessionManager? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var monitorJob: Job? = null
    private var activeVideoFile: File? = null

    private val _state = MutableStateFlow(TorrentStreamState())
    val state: StateFlow<TorrentStreamState> = _state

    fun startMagnetStream(magnetUri: String) {
        stop()
        _state.value = TorrentStreamState(statusText = "جاري تشغيل محرك التورنت المدمج...")

        monitorJob = scope.launch {
            try {
                val baseDir = File(context.cacheDir, "filmr_torrent_stream").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }
                val mgr = SessionManager()
                session = mgr
                mgr.start()

                _state.value = _state.value.copy(statusText = "جاري جلب بيانات التورنت (Metadata)...")
                val metadataBytes = mgr.fetchMagnet(magnetUri, 30, baseDir)
                if (metadataBytes == null || metadataBytes.isEmpty()) {
                    _state.value = TorrentStreamState(error = "تعذر العثور على مصادر نشطة لهذا التورنت")
                    return@launch
                }

                val torrentInfo = TorrentInfo.bdecode(metadataBytes)
                val filesCount = torrentInfo.numFiles()
                var bestIndex = 0
                var bestSize = -1L
                val videoExts = listOf(".mkv", ".mp4", ".avi", ".webm", ".mov")

                for (i in 0 until filesCount) {
                    val path = torrentInfo.files().filePath(i).lowercase()
                    val size = torrentInfo.files().fileSize(i)
                    val isVideo = videoExts.any { path.endsWith(it) }
                    if (isVideo && size > bestSize) {
                        bestSize = size
                        bestIndex = i
                    }
                }
                if (bestSize <= 0L) {
                    for (i in 0 until filesCount) {
                        val size = torrentInfo.files().fileSize(i)
                        if (size > bestSize) {
                            bestSize = size
                            bestIndex = i
                        }
                    }
                }

                mgr.download(torrentInfo, baseDir)
                val handle: TorrentHandle = mgr.find(torrentInfo.infoHash()) ?: run {
                    _state.value = TorrentStreamState(error = "فشل بدء تحميل التورنت")
                    return@launch
                }

                val priorities = Array(filesCount) { idx ->
                    if (idx == bestIndex) Priority.SEVEN else Priority.IGNORE
                }
                handle.prioritizeFiles(priorities)
                handle.setSequentialDownload(true)

                val targetFile = File(baseDir, torrentInfo.files().filePath(bestIndex))
                activeVideoFile = targetFile

                val port = startLocalHttpServer(targetFile, bestSize)
                val streamUrl = "http://127.0.0.1:$port/video.mp4"
                var announcedReady = false

                while (isActive) {
                    val st = handle.status()
                    val prog = (st.progress() * 100f).toInt().coerceIn(0, 100)
                    val speedKb = (st.downloadPayloadRate() / 1024).coerceAtLeast(0)
                    val peersCount = st.numPeers()
                    val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

                    if (!announcedReady && (existingBytes > 4 * 1024 * 1024L || prog >= 3)) {
                        announcedReady = true
                        _state.value = TorrentStreamState(
                            statusText = "جاري البث المباشر",
                            progressPercent = prog,
                            downloadSpeedKb = speedKb,
                            peers = peersCount,
                            localStreamUrl = streamUrl
                        )
                    } else {
                        _state.value = _state.value.copy(
                            statusText = if (announcedReady) "جاري البث المباشر" else "جاري التحميل المسبق ($prog%)...",
                            progressPercent = prog,
                            downloadSpeedKb = speedKb,
                            peers = peersCount,
                            localStreamUrl = if (announcedReady) streamUrl else null
                        )
                    }
                    delay(1000L)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _state.value = TorrentStreamState(error = "خطأ في التورنت: ${e.message ?: "غير معروف"}")
                }
            }
        }
    }

    private fun startLocalHttpServer(file: File, totalLength: Long): Int {
        val srv = ServerSocket(0)
        serverSocket = srv
        val port = srv.localPort

        serverJob = scope.launch {
            while (isActive && !srv.isClosed) {
                try {
                    val client = srv.accept()
                    launch { handleHttpClient(client, file, totalLength) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return port
    }

    private suspend fun handleHttpClient(socket: Socket, file: File, totalLength: Long) {
        socket.use { s ->
            val input = s.getInputStream().bufferedReader()
            val out = s.getOutputStream()
            val requestLine = input.readLine() ?: return
            if (!requestLine.startsWith("GET")) return

            var rangeHeader: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(":").trim()
                }
            }

            var waitTries = 0
            while (!file.exists() && waitTries < 40) {
                delay(250L)
                waitTries++
            }
            if (!file.exists()) return

            val fileLen = maxOf(totalLength, file.length())
            var start = 0L
            var end = fileLen - 1

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val spec = rangeHeader.removePrefix("bytes=")
                val parts = spec.split("-")
                start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val reqEnd = parts.getOrNull(1)?.toLongOrNull()
                if (reqEnd != null && reqEnd < fileLen) {
                    end = reqEnd
                }
            }

            val contentLength = (end - start + 1).coerceAtLeast(0L)
            val headers = buildString {
                if (rangeHeader != null) {
                    append("HTTP/1.1 206 Partial Content
")
                    append("Content-Range: bytes $start-$end/$fileLen
")
                } else {
                    append("HTTP/1.1 200 OK
")
                }
                append("Content-Type: video/mp4
")
                append("Accept-Ranges: bytes
")
                append("Content-Length: $contentLength
")
                append("Connection: close

")
            }
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.flush()

            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(64 * 1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val available = file.length() - raf.filePointer
                    if (available <= 0L) {
                        delay(200L)
                        continue
                    }
                    val toRead = minOf(buf.size.toLong(), remaining, available).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    remaining -= read
                }
                out.flush()
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        try {
            session?.stop()
        } catch (_: Exception) {
        }
        session = null
        _state.value = TorrentStreamState()
    }
}
