package com.filmr.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioChip
import com.filmr.tv.ui.components.NuvioGold
import kotlinx.coroutines.delay

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
    var isPlaying by remember { mutableStateOf(true) }
    var selectedSubIndex by remember { mutableIntStateOf(-1) }

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
            val mediaBuilder = ExoMediaItem.Builder().setUri(stream.url)
            exoPlayer.setMediaItem(mediaBuilder.build())
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

    LaunchedEffect(selectedSubIndex, subtitles) {
        if (selectedSubIndex in subtitles.indices) {
            val sub = subtitles[selectedSubIndex]
            val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
            val pos = exoPlayer.currentPosition
            val subConfig = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(MimeTypes.TEXT_VTT)
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
        while (true) {
            delay(5000L)
            isPlaying = exoPlayer.isPlaying
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (dur > 0L && pos > 0L) {
                viewModel.saveWatchProgress(item, season, episode, pos, dur)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            viewModel.torrentEngine.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
            modifier = Modifier.fillMaxSize()
        )

        if ((stream.isTorrent || stream.url.startsWith("magnet:", true)) && localTorrentUrl == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (torrentState.error != null) {
                    Text(torrentState.error ?: "", color = Color.Red, fontWeight = FontWeight.Bold)
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

        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NuvioChip("رجوع", icon = Icons.Default.ArrowBack) { onBack() }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = item.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stream.addonName} • ${stream.quality}",
                            color = NuvioGold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NuvioChip("ترجيع 15ث", icon = Icons.Default.Replay) {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L))
                    }
                    NuvioChip(
                        label = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        selected = true,
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                    ) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    }
                    NuvioChip("تقديم 15ث", icon = Icons.Default.Forward10) {
                        exoPlayer.seekTo(exoPlayer.currentPosition + 15_000L)
                    }
                    if (subtitles.isNotEmpty()) {
                        val nextIdx = if (selectedSubIndex + 1 < subtitles.size) selectedSubIndex + 1 else -1
                        val subLabel = if (selectedSubIndex in subtitles.indices) subtitles[selectedSubIndex].label else "بدون ترجمة"
                        NuvioChip("الترجمة: $subLabel", icon = Icons.Default.Subtitles) {
                            selectedSubIndex = nextIdx
                        }
                    }
                    NuvioChip("إخفاء الأزرار") {
                        showControls = false
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                NuvioChip("إظهار التحكم") { showControls = true }
            }
        }
    }
}
