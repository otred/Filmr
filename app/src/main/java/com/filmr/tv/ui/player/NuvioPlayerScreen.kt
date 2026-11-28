package com.filmr.tv.ui.player

import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioCardBg
import com.filmr.tv.ui.components.NuvioChip
import com.filmr.tv.ui.components.NuvioCircleButton
import com.filmr.tv.ui.components.NuvioGold
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NuvioPlayerScreen(
    viewModel: FilmrViewModel,
    item: MediaItem,
    stream: UnifiedStream,
    season: Int,
    episode: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val torrentState by viewModel.torrentEngine.state.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showPauseInfo by remember { mutableStateOf(false) }
    var showSubtitlesModal by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var selectedSubLang by remember { mutableStateOf("ar") }
    var selectedSubIndex by remember { mutableIntStateOf(-1) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var clockText by remember { mutableStateOf("") }
    var endsAtText by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(stream.url) {
        viewModel.loadSubtitlesForPlayback(item, season, episode)
        if (stream.isTorrent || stream.url.startsWith("magnet:", true)) {
            viewModel.torrentEngine.startMagnetStream(stream.url)
        } else {
            exoPlayer.setMediaItem(ExoMediaItem.Builder().setUri(stream.url).build())
            exoPlayer.prepare()
        }
    }

    val localTorrentUrl = torrentState.localStreamUrl
    LaunchedEffect(localTorrentUrl) {
        if (!localTorrentUrl.isNullOrBlank()) {
            exoPlayer.setMediaItem(ExoMediaItem.fromUri(localTorrentUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Auto-select first Arabic subtitle when loaded
    LaunchedEffect(subtitles) {
        if (selectedSubIndex == -1 && subtitles.isNotEmpty()) {
            val arIdx = subtitles.indexOfFirst { it.language.startsWith("ar", true) }
            if (arIdx >= 0) selectedSubIndex = arIdx
        }
    }

    LaunchedEffect(selectedSubIndex, subtitles) {
        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
        val pos = exoPlayer.currentPosition
        if (selectedSubIndex in subtitles.indices) {
            val sub = subtitles[selectedSubIndex]
            val mime = if (sub.url.contains(".srt", true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
            val subConfig = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(mime)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val updated = ExoMediaItem.Builder()
                .setUri(currentUri)
                .setSubtitleConfigurations(listOf(subConfig))
                .build()
            exoPlayer.setMediaItem(updated)
            exoPlayer.seekTo(pos)
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("h:mm a", Locale("ar"))
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            val now = System.currentTimeMillis()
            clockText = fmt.format(Date(now))
            val rem = (durationMs - currentPosMs).coerceAtLeast(0L)
            endsAtText = if (rem > 0L) "ينتهي عند ${fmt.format(Date(now + rem))}" else ""
            if (durationMs > 0L && currentPosMs > 0L) {
                viewModel.saveWatchProgress(item, season, episode, currentPosMs, durationMs)
            }
            delay(1000L)
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying && durationMs > 0L) {
            delay(2500L)
            if (!exoPlayer.isPlaying && !showSubtitlesModal) {
                showPauseInfo = true
            }
        } else {
            showPauseInfo = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            viewModel.torrentEngine.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    if (showSubtitlesModal && ev.key == Key.Back) {
                        showSubtitlesModal = false
                        true
                    } else if (!showControls && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.DirectionUp || ev.key == Key.DirectionDown)) {
                        showControls = true
                        true
                    } else false
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { pv ->
                val textAlpha = ((settings.subtitleTextOpacity / 100f) * 255).toInt().coerceIn(0, 255)
                val baseRgb = (settings.subtitleColorHex and 0x00FFFFFFL).toInt()
                val fgColor = (textAlpha shl 24) or baseRgb
                val bgAlpha = ((settings.subtitleBgOpacity / 100f) * 255).toInt().coerceIn(0, 255)
                val bgColor = (bgAlpha shl 24)
                val edgeType = if (settings.subtitleOutline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE
                val style = CaptionStyleCompat(
                    fgColor,
                    bgColor,
                    android.graphics.Color.TRANSPARENT,
                    edgeType,
                    android.graphics.Color.BLACK,
                    if (settings.subtitleBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                )
                pv.subtitleView?.setStyle(style)
                val sizeSp = 22f * (settings.subtitleFontPercent / 100f)
                pv.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Built-in Torrent Buffering Overlay
        if ((stream.isTorrent || stream.url.startsWith("magnet:", true)) && localTorrentUrl == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (torrentState.error != null) {
                    Text(torrentState.error ?: "", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    NuvioChip("رجوع للسيرفرات") { onBack() }
                } else {
                    CircularProgressIndicator(color = NuvioGold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(torrentState.statusText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "السرعة: ${torrentState.downloadSpeedKb} KB/s • المتصلون: ${torrentState.peers}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Pause Info Overlay ("أنت تشاهد" + Cast like NuvioTV)
        if (showPauseInfo && !showSubtitlesModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(36.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.68f)) {
                    Text("أنت تشاهد", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.displayTitle(settings.preferEnglishTitles),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (item.year.isNotBlank()) {
                        Text(item.year, color = Color.LightGray, fontSize = 14.sp)
                    }
                    if (item.overviewAr.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(item.overviewAr, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.cast.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("طاقم العمل", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.cast.take(5).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .background(NuvioCardBg, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(c.name, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Player Controls HUD
        if (showControls && !showSubtitlesModal) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(24.dp)
            ) {
                // Top Bar: Back + Clock & Ends At on Left
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NuvioCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") { onBack() }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        if (clockText.isNotBlank()) {
                            Text(clockText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (endsAtText.isNotBlank()) {
                            Text(endsAtText, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Title & Remaining Time Row above Seekbar
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = item.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.year.isNotBlank()) {
                            Text(item.year, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val remMs = (durationMs - currentPosMs).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remMs)} / ${formatTime(currentPosMs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val progressFraction = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NuvioCircleButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "تشغيل/إيقاف",
                        active = true
                    ) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    }

                    NuvioCircleButton(Icons.Default.Replay10, "ترجيع 10 ثواني") {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                    }

                    NuvioCircleButton(Icons.Default.Forward10, "تقديم 10 ثواني") {
                        exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                    }

                    NuvioCircleButton(Icons.Default.Subtitles, "الترجمة") {
                        showSubtitlesModal = true
                    }

                    NuvioChip("إخفاء") { showControls = false }
                }
            }
        }

        // 3-Column Nuvio Subtitles Modal
        if (showSubtitlesModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131318))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الترجمة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        NuvioCircleButton(Icons.Default.Close, "إغلاق") { showSubtitlesModal = false }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Column 1 (Right): Languages
                        Column(modifier = Modifier.weight(0.26f).fillMaxHeight()) {
                            Text("اللغات", color = Color.LightGray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            val langs = listOf("off" to "بدون", "ar" to "العربية", "en" to "English")
                            langs.forEach { (code, label) ->
                                NuvioChip(
                                    label = label,
                                    selected = selectedSubLang == code,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    selectedSubLang = code
                                    if (code == "off") selectedSubIndex = -1
                                }
                            }
                        }

                        // Column 2 (Middle): Available Subtitle Tracks
                        Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                            Text("الترجمة المتاحة (${subtitles.size})", color = Color.LightGray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            val filteredSubs = when (selectedSubLang) {
                                "off" -> emptyList()
                                "ar" -> subtitles.filter { it.language.startsWith("ar", true) }.ifEmpty { subtitles }
                                else -> subtitles
                            }
                            if (filteredSubs.isEmpty()) {
                                Text("لا توجد ترجمات مختارة", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(filteredSubs) { _, sub ->
                                        val realIndex = subtitles.indexOf(sub)
                                        val isPicked = realIndex == selectedSubIndex
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isPicked) Color.White.copy(alpha = 0.2f) else NuvioCardBg)
                                                .border(
                                                    width = if (isPicked) 2.dp else 1.dp,
                                                    color = if (isPicked) Color.White else Color.White.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .focusable()
                                                .clickable { selectedSubIndex = realIndex }
                                                .padding(10.dp)
                                        ) {
                                            Text(sub.addonName, color = NuvioGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(sub.label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        // Column 3 (Left): Subtitle Styling Controls
                        LazyColumn(
                            modifier = Modifier.weight(0.32f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("نمط الترجمة", color = Color.LightGray, fontSize = 13.sp)
                            }
                            item {
                                Text("حجم الخط: ${settings.subtitleFontPercent}%", color = Color.White, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NuvioChip("-") {
                                        viewModel.updateSettings(settings.copy(subtitleFontPercent = (settings.subtitleFontPercent - 10).coerceAtLeast(60)))
                                    }
                                    NuvioChip("+") {
                                        viewModel.updateSettings(settings.copy(subtitleFontPercent = (settings.subtitleFontPercent + 10).coerceAtMost(180)))
                                    }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NuvioChip("عريض", selected = settings.subtitleBold) {
                                        viewModel.updateSettings(settings.copy(subtitleBold = !settings.subtitleBold))
                                    }
                                    NuvioChip("إطار خارجي", selected = settings.subtitleOutline) {
                                        viewModel.updateSettings(settings.copy(subtitleOutline = !settings.subtitleOutline))
                                    }
                                }
                            }
                            item {
                                Text("لون النص", color = Color.White, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val colors = listOf(0xFFFFFFFFL, 0xFFF5C518L, 0xFF38BDF8L, 0xFF4ADE80L)
                                    colors.forEach { hex ->
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                                .border(
                                                    width = if (settings.subtitleColorHex == hex) 2.5.dp else 1.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                                .focusable()
                                                .clickable {
                                                    viewModel.updateSettings(settings.copy(subtitleColorHex = hex))
                                                }
                                        )
                                    }
                                }
                            }
                            item {
                                Text("تعتيم الخلفية: ${settings.subtitleBgOpacity}%", color = Color.White, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NuvioChip("-") {
                                        viewModel.updateSettings(settings.copy(subtitleBgOpacity = (settings.subtitleBgOpacity - 15).coerceAtLeast(0)))
                                    }
                                    NuvioChip("+") {
                                        viewModel.updateSettings(settings.copy(subtitleBgOpacity = (settings.subtitleBgOpacity + 15).coerceAtMost(100)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
