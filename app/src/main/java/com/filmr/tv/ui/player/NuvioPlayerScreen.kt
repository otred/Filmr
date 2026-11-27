package com.filmr.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.data.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun NuvioPlayerScreen(
    media: MediaItem,
    stream: UnifiedStream,
    availableSubtitles: List<SubtitleTrack>,
    season: Int,
    episode: Int,
    episodeTitle: String,
    initialPositionMs: Long = 0L,
    viewModel: FilmrViewModel,
    onBackToServers: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val torrentStatus by viewModel.torrentEngine.status.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showSubtitleStudio by remember { mutableStateOf(false) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrack?>(
        availableSubtitles.firstOrNull { it.lang.lowercase().startsWith("ar") }
            ?: availableSubtitles.firstOrNull()
    ) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var resolvedPlayUrl by remember { mutableStateOf<String?>(null) }

    // Start Built-in Torrent Engine if stream is Torrent / Magnet, otherwise use direct URL
    LaunchedEffect(stream.id) {
        if (stream.isTorrent) {
            val magnet = stream.url
                ?: stream.infoHash?.let { "magnet:?xt=urn:btih:$it" }
                ?: ""
            if (magnet.isNotBlank()) {
                viewModel.torrentEngine.startMagnetStream(
                    magnetUri = magnet,
                    preferredFileIdx = stream.fileIdx,
                    lowEndMode = settings.lowEndDeviceMode
                )
            }
        } else {
            resolvedPlayUrl = stream.url
        }
    }

    LaunchedEffect(torrentStatus.streamUrl) {
        if (stream.isTorrent && !torrentStatus.streamUrl.isNullOrBlank()) {
            resolvedPlayUrl = torrentStatus.streamUrl
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Load media + external subtitle into ExoPlayer when URL is ready
    LaunchedEffect(resolvedPlayUrl, selectedSubtitle) {
        val url = resolvedPlayUrl ?: return@LaunchedEffect
        val resumeAt = if (exoPlayer.currentPosition > 0L) exoPlayer.currentPosition else initialPositionMs

        val builder = Media3Item.Builder().setUri(Uri.parse(url))
        selectedSubtitle?.let { sub ->
            val subMime = when {
                sub.url.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                sub.url.endsWith(".ass", true) || sub.url.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subMime)
                .setLanguage(sub.lang)
                .setLabel(sub.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (resumeAt > 0L) {
            exoPlayer.seekTo(resumeAt)
        }
        exoPlayer.playWhenReady = true
    }

    // Track playback state & periodically save Watch Progress
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) showControls = true
            }
        }
        exoPlayer.addListener(listener)

        var saveCounter = 0
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            saveCounter++
            if (saveCounter % 10 == 0 && durationMs > 0L) {
                viewModel.saveWatchProgress(
                    media = media,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    positionMs = currentPosMs,
                    durationMs = durationMs
                )
            }
            delay(1000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveWatchProgress(
                media = media,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            )
            exoPlayer.release()
            viewModel.torrentEngine.stopCurrentStream(keepSession = true)
        }
    }

    BackHandler {
        if (showSubtitleStudio) {
            showSubtitleStudio = false
        } else {
            onBackToServers()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    subtitleView?.apply {
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.subtitleFontSizeSp)
                        setStyle(
                            CaptionStyleCompat(
                                settings.subtitleTextColorHex.toInt(),
                                android.graphics.Color.argb((settings.subtitleBgOpacity * 255).toInt(), 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                                if (settings.subtitleEdgeOutline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Built-in Torrent Buffering / Status Banner
        if (stream.isTorrent && resolvedPlayUrl == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(color = NuvioGold, strokeWidth = 3.dp)
                    Text(
                        text = "محرك التورنت المدمج (Filmr Torrent)",
                        color = NuvioGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = torrentStatus.error ?: torrentStatus.statusText,
                        color = if (torrentStatus.error != null) Color(0xFFEF4444) else Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    NuvioFocusButton(
                        text = "العودة لقائمة السيرفرات",
                        icon = Icons.Default.ArrowBack,
                        onClick = onBackToServers
                    )
                }
            }
        }

        // Nuvio Pause Information Overlay
        if (!isPlaying && resolvedPlayUrl != null && !showSubtitleStudio) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 42.dp, vertical = 84.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.62f)) {
                    Text(
                        text = "متوقف مؤقتاً",
                        color = NuvioGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = media.displayTitle(settings.preferEnglishTitles),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (season > 0 && episode > 0) {
                        Text(
                            text = "الموسم $season • الحلقة $episode ${if (episodeTitle.isNotBlank()) "- $episodeTitle" else ""}",
                            color = NuvioGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (media.overview.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = media.overview,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Top & Bottom Player HUD (Clock, ETA, Seekbar, Controls, Subtitle Studio)
        if (showControls && !showSubtitleStudio) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .padding(26.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Title + Live Clock & Ends At ETA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = media.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stream.addonName} • ${stream.quality}" +
                                if (stream.isTorrent && torrentStatus.peersCount > 0) " • متصلون: ${torrentStatus.peersCount} (${torrentStatus.downloadSpeedKb} KB/s)" else "",
                            color = NuvioGold,
                            fontSize = 12.sp
                        )
                    }

                    val nowTime = remember(currentPosMs) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    }
                    val etaTime = remember(currentPosMs, durationMs) {
                        val rem = (durationMs - currentPosMs).coerceAtLeast(0L)
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + rem))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = nowTime, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (durationMs > 0L) {
                            Text(text = "ينتهي الساعة $etaTime", color = NuvioTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Bottom Bar: Timeline + Control Buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val progress = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        color = NuvioGold,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NuvioFocusButton(
                                text = "-15 ث",
                                icon = Icons.Default.Replay,
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L)) }
                            )
                            NuvioFocusButton(
                                text = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                isPrimary = true,
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            )
                            NuvioFocusButton(
                                text = "+15 ث",
                                icon = Icons.Default.Forward10,
                                onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 15_000L) }
                            )
                            NuvioFocusButton(
                                text = "الترجمة (${availableSubtitles.size})",
                                icon = Icons.Default.Subtitles,
                                onClick = { showSubtitleStudio = true }
                            )
                            NuvioFocusButton(
                                text = "السيرفرات",
                                icon = Icons.Default.Dns,
                                onClick = onBackToServers
                            )
                        }
                    }
                }
            }
        }

        // Subtitle Studio Overlay
        if (showSubtitleStudio) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF121622),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("استوديو الترجمة", color = NuvioGold, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            NuvioFocusButton(text = "تم", icon = Icons.Default.Check, onClick = { showSubtitleStudio = false })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NuvioChip("حجم أصغر", false) {
                                viewModel.updateSettings { it.copy(subtitleFontSizeSp = (it.subtitleFontSizeSp - 2f).coerceAtLeast(14f)) }
                            }
                            NuvioChip("حجم أكبر (${settings.subtitleFontSizeSp.toInt()})", false) {
                                viewModel.updateSettings { it.copy(subtitleFontSizeSp = (it.subtitleFontSizeSp + 2f).coerceAtMost(38f)) }
                            }
                            NuvioChip("إيقاف الترجمة", selectedSubtitle == null) {
                                selectedSubtitle = null
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableSubtitles, key = { it.id }) { sub ->
                                val isSel = selectedSubtitle?.id == sub.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusable()
                                        .clickable { selectedSubtitle = sub },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) NuvioGold.copy(alpha = 0.25f) else NuvioCardBg,
                                    border = BorderStroke(1.dp, if (isSel) NuvioGold else Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text(
                                        text = sub.label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
