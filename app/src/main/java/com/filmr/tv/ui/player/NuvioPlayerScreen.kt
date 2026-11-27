package com.filmr.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.SubtitleTrack
import com.filmr.tv.data.model.UnifiedStream
import com.filmr.tv.data.torrent.BuiltInTorrentEngine
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioChip
import com.filmr.tv.ui.components.NuvioGold
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun NuvioPlayerScreen(
    item: MediaItem,
    initialStream: UnifiedStream,
    season: Int,
    episode: Int,
    episodeTitle: String,
    startPositionMs: Long,
    vm: FilmrViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val torrentState by BuiltInTorrentEngine.state.collectAsState()

    var isBuffering by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var showSubtitleStudio by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var onlineSubs by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var selectedSubLang by remember { mutableStateOf("ar") }
    var activeSubTrack by remember { mutableStateOf<SubtitleTrack?>(null) }
    var subOffsetSeconds by remember { mutableFloatStateOf(0f) }

    var currentPosMs by remember { mutableLongStateOf(startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentTimeText by remember { mutableStateOf("") }
    var finishTimeText by remember { mutableStateOf("") }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    val isTorrent = initialStream.isTorrent || initialStream.url.startsWith("magnet:", true)

    BackHandler {
        if (showSubtitleStudio) {
            showSubtitleStudio = false
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressMs < 2500L) {
                BuiltInTorrentEngine.stopCurrent()
                onBack()
            } else {
                lastBackPressMs = now
                showOverlay = true
            }
        }
    }

    val player = remember {
        val headers = mutableMapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 13; Android TV) FilmrTV/2.0")
        headers.putAll(initialStream.headers)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(headers)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPaused = !playing
                        if (!playing) showOverlay = true
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        errorMsg = "تعذر تشغيل الرابط (${error.errorCodeName})"
                    }
                })
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            BuiltInTorrentEngine.stopCurrent()
            player.release()
        }
    }

    LaunchedEffect(initialStream.url) {
        if (isTorrent) {
            BuiltInTorrentEngine.startMagnet(context, initialStream.url)
        } else {
            val exoItem = ExoMediaItem.fromUri(Uri.parse(initialStream.url))
            player.setMediaItem(exoItem)
            if (startPositionMs > 0) player.seekTo(startPositionMs)
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(torrentState.localStreamUrl) {
        val localUrl = torrentState.localStreamUrl ?: return@LaunchedEffect
        val exoItem = ExoMediaItem.fromUri(Uri.parse(localUrl))
        player.setMediaItem(exoItem)
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(item.id, season, episode) {
        onlineSubs = vm.fetchSubtitles(item, season, episode)
    }

    LaunchedEffect(activeSubTrack) {
        val sub = activeSubTrack ?: return@LaunchedEffect
        val playableUrl = torrentState.localStreamUrl ?: if (!isTorrent) initialStream.url else return@LaunchedEffect
        val pos = player.currentPosition
        val subConfig = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
            .setMimeType(if (sub.url.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(sub.language)
            .setLabel(sub.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val updated = ExoMediaItem.Builder()
            .setUri(Uri.parse(playableUrl))
            .setSubtitleConfigurations(listOf(subConfig))
            .build()
        player.setMediaItem(updated)
        if (pos > 0) player.seekTo(pos)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            val now = System.currentTimeMillis()
            currentTimeText = fmt.format(Date(now))
            val rem = (durationMs - currentPosMs).coerceAtLeast(0L)
            finishTimeText = if (durationMs > 0L) fmt.format(Date(now + rem)) else ""

            if (durationMs > 10_000L && currentPosMs > 5_000L) {
                vm.saveWatchProgress(
                    item = item,
                    season = season,
                    episode = episode,
                    positionMs = currentPosMs,
                    durationMs = durationMs,
                    streamUrl = initialStream.url,
                    addonName = initialStream.addonName
                )
            }
            delay(3000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showOverlay = !showOverlay }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { pv ->
                pv.player = player
                pv.subtitleView?.apply {
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.subtitleSizeSp)
                    val bgAlpha = (settings.subtitleBgOpacity * 255).toInt().coerceIn(0, 255)
                    val bgColor = android.graphics.Color.argb(bgAlpha, 0, 0, 0)
                    val edgeType = if (settings.subtitleOutline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE
                    setStyle(
                        CaptionStyleCompat(
                            settings.subtitleColorHex.toInt(),
                            bgColor,
                            android.graphics.Color.TRANSPARENT,
                            edgeType,
                            android.graphics.Color.BLACK,
                            if (settings.subtitleBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if ((isTorrent && torrentState.localStreamUrl == null && torrentState.error == null) || isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = NuvioGold, strokeWidth = 3.5.dp)
                    Text(
                        text = if (isTorrent && torrentState.statusText.isNotBlank()) torrentState.statusText else "جاري تحميل البث...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val finalError = torrentState.error ?: errorMsg
        if (finalError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0A0D12)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(finalError, color = Color(0xFFF87171), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    NuvioChip("العودة للسيرفرات", selected = true) {
                        BuiltInTorrentEngine.stopCurrent()
                        onBack()
                    }
                }
            }
        }

        if (showOverlay && finalError == null) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(currentTimeText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (finishTimeText.isNotBlank()) {
                        Text("ينتهي الساعة $finishTimeText", color = NuvioGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isPaused && !isBuffering) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(0.58f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC0A0D12))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("متوقف مؤقتاً", color = NuvioGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(item.displayTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = item.overviewAr.ifBlank { item.overviewEn },
                            color = Color(0xFFD6DCE8),
                            fontSize = 13.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xCC0A0D12))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.type == "series") "${item.displayTitle} • م$season ح$episode ${episodeTitle}" else item.displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val remMs = (durationMs - currentPosMs).coerceAtLeast(0L)
                        Text(
                            text = "${formatDuration(currentPosMs)}  /  -${formatDuration(remMs)}",
                            color = NuvioGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NuvioChip(if (isPaused) "▶ استئناف" else "❚❚ إيقاف", selected = true) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        NuvioChip("⏪ 15ث") { player.seekTo((player.currentPosition - 15_000L).coerceAtLeast(0L)) }
                        NuvioChip("⏩ 15ث") { player.seekTo(player.currentPosition + 15_000L) }
                        NuvioChip("الترجمة والمزامنة") { showSubtitleStudio = true }
                        NuvioChip("السيرفرات") {
                            BuiltInTorrentEngine.stopCurrent()
                            onBack()
                        }
                    }
                }
            }
        }

        if (showSubtitleStudio) {
            val filteredSubs = remember(onlineSubs, selectedSubLang) {
                if (selectedSubLang == "all") onlineSubs
                else onlineSubs.filter { it.language.startsWith(selectedSubLang, true) }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE607090E))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(190.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121722))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("اللغة", color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        NuvioChip("العربية", selected = selectedSubLang == "ar") { selectedSubLang = "ar" }
                        NuvioChip("English", selected = selectedSubLang == "en") { selectedSubLang = "en" }
                        NuvioChip("جميع اللغات", selected = selectedSubLang == "all") { selectedSubLang = "all" }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121722))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ملفات الترجمة (${filteredSubs.size})", color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredSubs, key = { it.id }) { sub ->
                                NuvioChip(
                                    label = sub.label,
                                    selected = activeSubTrack?.id == sub.id,
                                    onClick = { activeSubTrack = sub }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121722))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تخصيص الترجمة", color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            NuvioChip("إغلاق") { showSubtitleStudio = false }
                        }
                        Text("مزامنة التوقيت: ${String.format("%.1f", subOffsetSeconds)} ث", color = Color.White, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NuvioChip("-0.5 ث") { subOffsetSeconds -= 0.5f }
                            NuvioChip("تصفير") { subOffsetSeconds = 0f }
                            NuvioChip("+0.5 ث") { subOffsetSeconds += 0.5f }
                        }
                        Text("حجم الخط: ${settings.subtitleSizeSp.toInt()}", color = Color.White, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NuvioChip("تصغير -") { vm.updateSettings { it.copy(subtitleSizeSp = (it.subtitleSizeSp - 2f).coerceAtLeast(14f)) } }
                            NuvioChip("تكبير +") { vm.updateSettings { it.copy(subtitleSizeSp = (it.subtitleSizeSp + 2f).coerceAtMost(40f)) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NuvioChip("خط عريض", selected = settings.subtitleBold) {
                                vm.updateSettings { it.copy(subtitleBold = !it.subtitleBold) }
                            }
                            NuvioChip("إطار خارجي", selected = settings.subtitleOutline) {
                                vm.updateSettings { it.copy(subtitleOutline = !it.subtitleOutline) }
                            }
                        }
                        Text("لون النص", color = Color.White, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NuvioChip("أبيض", selected = settings.subtitleColorHex == 0xFFFFFFFF) {
                                vm.updateSettings { it.copy(subtitleColorHex = 0xFFFFFFFF) }
                            }
                            NuvioChip("أصفر", selected = settings.subtitleColorHex == 0xFFFFD600) {
                                vm.updateSettings { it.copy(subtitleColorHex = 0xFFFFD600) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
