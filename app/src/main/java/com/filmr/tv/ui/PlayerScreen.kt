package com.filmr.tv.ui

import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.engine.ArabicSubtitleSanitizer
import com.filmr.tv.model.PlayerEngineType
import com.filmr.tv.model.PlayerSettings
import com.filmr.tv.model.SubtitleTrack
import com.filmr.tv.model.UnifiedStream
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun DualPlayerScreen(
    stream: UnifiedStream,
    availableSubtitles: List<SubtitleTrack>,
    settings: PlayerSettings,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(true) }
    var activePanel by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrack?>(availableSubtitles.firstOrNull { it.language.startsWith("ar") }) }
    var fixedSubtitleUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(selectedSubtitle) {
        val sub = selectedSubtitle
        if (sub != null) {
            val file = ArabicSubtitleSanitizer.downloadAndFixSubtitle(context, sub.url, sub.id)
            fixedSubtitleUri = file?.let { Uri.fromFile(it) }
        } else {
            fixedSubtitleUri = null
        }
    }

    val exoPlayer = remember(stream.url, fixedSubtitleUri, settings.engine) {
        if (settings.engine == PlayerEngineType.EXOPLAYER) {
            ExoPlayer.Builder(context).build().apply {
                val subConfigs = fixedSubtitleUri?.let { uri ->
                    listOf(
                        Media3Item.SubtitleConfiguration.Builder(uri)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("ar")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                } ?: emptyList()

                val mediaItem = Media3Item.Builder()
                    .setUri(stream.url)
                    .setSubtitleConfigurations(subConfigs)
                    .build()

                setMediaItem(mediaItem)
                playbackParameters = PlaybackParameters(settings.playbackSpeed)
                prepare()
                playWhenReady = true
            }
        } else null
    }

    val vlcHolder = remember(stream.url, settings.engine) {
        if (settings.engine == PlayerEngineType.LIBVLC) {
            val libVlc = LibVLC(context, arrayListOf("--network-caching=2500", "--avcodec-hw=any"))
            val vlcPlayer = VlcMediaPlayer(libVlc)
            val media = Media(libVlc, Uri.parse(stream.url))
            vlcPlayer.media = media
            media.release()
            vlcPlayer.play()
            Pair(libVlc, vlcPlayer)
        } else null
    }

    LaunchedEffect(vlcHolder, fixedSubtitleUri, settings.subtitleOffsetMs, settings.audioDelayMs, settings.audioVolumeBoost, settings.playbackSpeed) {
        val vlc = vlcHolder?.second ?: return@LaunchedEffect
        fixedSubtitleUri?.let { vlc.addSlave(Media.Slave.Type.Subtitle, it, true) }
        vlc.setSpuDelay(settings.subtitleOffsetMs * 1000L)
        vlc.setAudioDelay(settings.audioDelayMs * 1000L)
        vlc.volume = settings.audioVolumeBoost
        vlc.rate = settings.playbackSpeed
    }

    LaunchedEffect(exoPlayer, settings.playbackSpeed, settings.audioVolumeBoost) {
        exoPlayer?.playbackParameters = PlaybackParameters(settings.playbackSpeed)
        exoPlayer?.volume = (settings.audioVolumeBoost / 100f).coerceIn(0f, 2f)
    }

    LaunchedEffect(exoPlayer, vlcHolder) {
        while (true) {
            if (exoPlayer != null) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(1L)
                isPlaying = exoPlayer.isPlaying
            } else if (vlcHolder != null) {
                currentPosMs = vlcHolder.second.time.coerceAtLeast(0L)
                durationMs = vlcHolder.second.length.coerceAtLeast(1L)
                isPlaying = vlcHolder.second.isPlaying
            }
            delay(500)
        }
    }

    DisposableEffect(exoPlayer, vlcHolder) {
        onDispose {
            exoPlayer?.release()
            vlcHolder?.second?.stop()
            vlcHolder?.second?.release()
            vlcHolder?.first?.release()
        }
    }

    fun seekBySeconds(deltaSec: Int) {
        val deltaMs = deltaSec * 1000L
        if (exoPlayer != null) {
            exoPlayer.seekTo((exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs))
        } else if (vlcHolder != null) {
            vlcHolder.second.time = (vlcHolder.second.time + deltaMs).coerceIn(0L, durationMs)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        } else if (vlcHolder != null) {
            if (vlcHolder.second.isPlaying) vlcHolder.second.pause() else vlcHolder.second.play()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    showControls = true
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (activePanel == null) { seekBySeconds(settings.seekStepSeconds); true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (activePanel == null) { seekBySeconds(-settings.seekStepSeconds); true } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            if (activePanel == null && !showControls) { togglePlayPause(); true } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (activePanel != null) { activePanel = null; true } else { onBack(); true }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (settings.engine == PlayerEngineType.EXOPLAYER && exoPlayer != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                update = { view ->
                    view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.subtitleSizeSp)
                    view.subtitleView?.setStyle(
                        CaptionStyleCompat(
                            settings.subtitleColorHex.toInt(),
                            android.graphics.Color.argb((settings.subtitleBgAlpha * 255).toInt(), 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            )
        } else if (vlcHolder != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout ->
                        vlcHolder.second.attachViews(layout, null, true, false)
                    }
                }
            )
        }

        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Column {
                            Text(stream.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("المصدر: ${stream.sourceName} • المحرك: ${settings.engine.name}", color = Color(0xFFE50914), fontSize = 13.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { activePanel = if (activePanel == "ENGINE") null else "ENGINE" }) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("المحرك (${settings.engine.name})")
                        }
                        FilledTonalButton(onClick = { activePanel = if (activePanel == "SEEK") null else "SEEK" }) {
                            Icon(Icons.Default.Forward10, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("القفز: ${settings.seekStepSeconds}ث")
                        }
                        FilledTonalButton(onClick = { activePanel = if (activePanel == "AUDIO") null else "AUDIO" }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("الصوت")
                        }
                        FilledTonalButton(onClick = { activePanel = if (activePanel == "SUBS") null else "SUBS" }) {
                            Icon(Icons.Default.Subtitles, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("الترجمة (${availableSubtitles.size})")
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMillis(currentPosMs), color = Color.White)
                        Text(formatMillis(durationMs), color = Color.LightGray)
                    }
                    Slider(
                        value = (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { ratio ->
                            val target = (ratio * durationMs).toLong()
                            exoPlayer?.seekTo(target)
                            vlcHolder?.second?.time = target
                        },
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFE50914), activeTrackColor = Color(0xFFE50914))
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { seekBySeconds(-30) }) { Text("-30ث") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { seekBySeconds(-settings.seekStepSeconds) }) {
                            Text("-${settings.seekStepSeconds}ث")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = { togglePlayPause() },
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPlaying) "إيقاف مؤقت" else "تشغيل", color = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(onClick = { seekBySeconds(settings.seekStepSeconds) }) {
                            Text("+${settings.seekStepSeconds}ث")
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { seekBySeconds(30) }) { Text("+30ث") }
                    }
                }
            }
        }

        if (activePanel != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(380.dp)
                    .padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141821)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (activePanel) {
                        "ENGINE" -> {
                            Text("اختيار محرك التشغيل والسرعة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Button(
                                onClick = { onUpdateSettings { it.copy(engine = PlayerEngineType.EXOPLAYER) } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Media3 ExoPlayer (الأسرع للبث و HDR)") }
                            Button(
                                onClick = { onUpdateSettings { it.copy(engine = PlayerEngineType.LIBVLC) } },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("LibVLC Engine (الأقوى للأنمي و MKV و ASS)") }
                            Text("سرعة التشغيل: ${settings.playbackSpeed}x", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    FilterChip(
                                        selected = settings.playbackSpeed == spd,
                                        onClick = { onUpdateSettings { it.copy(playbackSpeed = spd) } },
                                        label = { Text("${spd}x") }
                                    )
                                }
                            }
                        }

                        "SEEK" -> {
                            Text("مدة التقديم والترجيع الافتراضية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            listOf(5, 10, 15, 30).forEach { sec ->
                                Button(
                                    onClick = {
                                        onUpdateSettings { it.copy(seekStepSeconds = sec) }
                                        activePanel = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("$sec ثواني", color = Color.White)
                                }
                            }
                        }

                        "AUDIO" -> {
                            Text("إعدادات الصوت المتقدمة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("تضخيم الصوت: ${settings.audioVolumeBoost}%", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(100, 125, 150, 200).forEach { vol ->
                                    FilterChip(
                                        selected = settings.audioVolumeBoost == vol,
                                        onClick = { onUpdateSettings { it.copy(audioVolumeBoost = vol) } },
                                        label = { Text("$vol%") }
                                    )
                                }
                            }
                            Text("مزامنة الصوت: ${settings.audioDelayMs} ms", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(audioDelayMs = it.audioDelayMs - 100) } }) { Text("-0.1s") }
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(audioDelayMs = 0) } }) { Text("تصفير") }
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(audioDelayMs = it.audioDelayMs + 100) } }) { Text("+0.1s") }
                            }
                        }

                        "SUBS" -> {
                            Text("إعدادات واختيار الترجمة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("حجم الخط: ${settings.subtitleSizeSp.toInt()} • التزامن: ${settings.subtitleOffsetMs}ms", color = Color.LightGray, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(subtitleSizeSp = (it.subtitleSizeSp - 2f).coerceAtLeast(14f)) } }) { Text("خط -") }
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(subtitleSizeSp = (it.subtitleSizeSp + 2f).coerceAtMost(44f)) } }) { Text("خط +") }
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(subtitleOffsetMs = it.subtitleOffsetMs - 200) } }) { Text("-0.2s") }
                                OutlinedButton(onClick = { onUpdateSettings { it.copy(subtitleOffsetMs = it.subtitleOffsetMs + 200) } }) { Text("+0.2s") }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(availableSubtitles) { sub ->
                                    Button(
                                        onClick = { selectedSubtitle = sub },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(sub.label, color = Color.White, maxLines = 1)
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

private fun formatMillis(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
