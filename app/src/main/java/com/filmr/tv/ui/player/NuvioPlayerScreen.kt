package com.filmr.tv.ui.player

import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(UnstableApi::class)
@Composable
fun NuvioPlayerScreen(
    mediaItem: MediaItem,
    initialStream: StreamSource,
    allStreams: List<StreamSource>,
    season: Int?,
    episode: Int?,
    startPositionMs: Long,
    settings: AppSettings,
    onSaveProgress: (Long, Long) -> Unit,
    onSwitchStreamRequested: (StreamSource, Long) -> Unit,
    onExitPlayer: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(settings.accentColorHex)

    var activeStream by remember { mutableStateOf(initialStream) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerErrorMsg by remember { mutableStateOf<String?>(null) }
    var currentPosMs by remember { mutableLongStateOf(startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Overlays inside player
    var showServersPanel by remember { mutableStateOf(false) }
    var showSubtitlesPanel by remember { mutableStateOf(false) }
    var selectedSubUrl by remember { mutableStateOf(initialStream.subtitles.firstOrNull()?.url) }
    var subFontSizeSp by remember { mutableIntStateOf(settings.subtitleTextSizeSp) }
    var resizeModeIndex by remember { mutableIntStateOf(0) } // 0=Fit, 1=Zoom, 2=Fill

    val playPauseFocus = remember { FocusRequester() }

    // Build ExoPlayer with custom HTTP headers & external Arabic subtitle tracks
    val exoPlayer = remember(activeStream, selectedSubUrl) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .apply {
                if (activeStream.headers.isNotEmpty()) {
                    setDefaultRequestProperties(activeStream.headers)
                }
            }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val streamUri = Uri.parse(activeStream.url ?: "")
                val itemBuilder = Media3Item.Builder().setUri(streamUri)

                // Attach external Arabic subtitle if selected
                val chosenSub = activeStream.subtitles.find { it.url == selectedSubUrl }
                if (chosenSub != null) {
                    val mime = when {
                        chosenSub.url.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                        chosenSub.url.endsWith(".ass", true) || chosenSub.url.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    val subConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(chosenSub.url))
                        .setMimeType(mime)
                        .setLanguage("ar")
                        .setLabel(chosenSub.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    itemBuilder.setSubtitleConfigurations(listOf(subConfig))
                }

                setMediaItem(itemBuilder.build())
                if (startPositionMs > 0L) seekTo(startPositionMs)
                playWhenReady = true
                prepare()
            }
    }

    // Player Listener + Periodic Progress & Clock Update
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playerErrorMsg = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerErrorMsg = "تعذر تشغيل هذا السيرفر (${error.errorCodeName}). اختر سيرفراً آخر من قائمة السيرفرات."
                controlsVisible = true
                showServersPanel = true
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            onSaveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Progress polling & auto-hide controls after 5 seconds
    LaunchedEffect(exoPlayer, controlsVisible, lastInteractionTime, showServersPanel, showSubtitlesPanel) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (controlsVisible && !showServersPanel && !showSubtitlesPanel && isPlaying) {
                if (System.currentTimeMillis() - lastInteractionTime > 5000L) {
                    controlsVisible = false
                }
            }
            delay(500L)
        }
    }

    // Calculate Current Clock & Estimated Finish Time (Nuvio Top-Left feature!)
    val nowTimeStr = remember(currentPosMs) {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val finishTimeStr = remember(currentPosMs, durationMs) {
        if (durationMs > currentPosMs) {
            val remSec = (durationMs - currentPosMs) / 1000L
            LocalTime.now().plusSeconds(remSec).format(DateTimeFormatter.ofPattern("HH:mm"))
        } else "--:--"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    lastInteractionTime = System.currentTimeMillis()
                    if (!controlsVisible) {
                        controlsVisible = true
                        return@onPreviewKeyEvent true
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showServersPanel && !showSubtitlesPanel) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 15_000L).coerceAtMost(durationMs))
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showServersPanel && !showSubtitlesPanel) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L))
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        // ExoPlayer Video Surface
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
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeModeIndex
                view.subtitleView?.apply {
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subFontSizeSp.toFloat())
                    setStyle(
                        CaptionStyleCompat(
                            Color(settings.subtitleColorHex).toArgb(),
                            Color.Black.copy(alpha = settings.subtitleBgOpacity).toArgb(),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Spinner
        if (isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("جاري تحميل البث...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Error Banner if stream fails
        if (playerErrorMsg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xEE991B1B))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(playerErrorMsg!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Nuvio Cinematic HUD Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xCC000000),
                            0.35f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1.0f to Color(0xEE000000)
                        )
                    )
                    .padding(28.dp)
            ) {
                // Top Bar: Right = Title & Episode | Left = Current Clock & Finish Time (Exact Nuvio style!)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onExitPlayer) {
                            Icon(Icons.Rounded.ArrowForward, contentDescription = "رجوع", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = mediaItem.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            val subInfo = if (season != null && episode != null) {
                                "الموسم $season • الحلقة $episode  |  ${activeStream.addonName} (${activeStream.quality})"
                            } else {
                                "${activeStream.addonName} (${activeStream.quality})"
                            }
                            Text(text = subInfo, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Nuvio Clock + End Time Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x9914161D))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = nowTimeStr, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "ينتهي الساعة $finishTimeStr", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // Bottom Bar: Seek Progress + Play/Pause/Seek + Subtitles + Servers + Aspect Ratio
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    val progress = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accentColor,
                        trackColor = Color(0x44FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(formatDurationMs(currentPosMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(formatDurationMs(durationMs), color = TextSecondary, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Playback Transport Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayerControlPill(
                                icon = Icons.Rounded.Replay10,
                                label = "-15ث",
                                accentColor = accentColor,
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L)) }
                            )
                            PlayerControlPill(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                label = if (isPlaying) "إيقاف" else "تشغيل",
                                accentColor = accentColor,
                                isPrimary = true,
                                modifier = Modifier.focusRequester(playPauseFocus),
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
                            )
                            PlayerControlPill(
                                icon = Icons.Rounded.Forward10,
                                label = "+15ث",
                                accentColor = accentColor,
                                onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 15_000L).coerceAtMost(durationMs)) }
                            )
                        }

                        // Nuvio Extra Tools: Subtitles, Switch Server, Aspect Ratio
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayerControlPill(
                                icon = Icons.Rounded.Subtitles,
                                label = "الترجمة (${activeStream.subtitles.size})",
                                accentColor = accentColor,
                                onClick = {
                                    showSubtitlesPanel = !showSubtitlesPanel
                                    showServersPanel = false
                                }
                            )
                            PlayerControlPill(
                                icon = Icons.Rounded.Dns,
                                label = "السيرفرات (${allStreams.size})",
                                accentColor = accentColor,
                                onClick = {
                                    showServersPanel = !showServersPanel
                                    showSubtitlesPanel = false
                                }
                            )
                            PlayerControlPill(
                                icon = Icons.Rounded.AspectRatio,
                                label = when (resizeModeIndex) {
                                    1 -> "تكبير"
                                    2 -> "ملء الشاشة"
                                    else -> "أصلي"
                                },
                                accentColor = accentColor,
                                onClick = { resizeModeIndex = (resizeModeIndex + 1) % 3 }
                            )
                        }
                    }
                }
            }
        }

        // In-Player Servers Switcher Drawer
        if (showServersPanel) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .align(Alignment.CenterStart)
                    .background(Color(0xF5101218))
                    .border(1.dp, accentColor.copy(alpha = 0.4f))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تبديل السيرفر", color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showServersPanel = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allStreams) { st ->
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            val isCurrent = st.id == activeStream.id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) accentColor.copy(alpha = 0.25f) else if (focused) Color(0x33FFFFFF) else NuvioCard)
                                    .border(1.5.dp, if (focused || isCurrent) accentColor else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                    .focusable(interactionSource = interaction)
                                    .clickable(interactionSource = interaction, indication = null) {
                                        showServersPanel = false
                                        onSwitchStreamRequested(st, currentPosMs)
                                    }
                                    .padding(12.dp)
                            ) {
                                Text("${st.addonName} • ${st.quality}", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(st.description.ifBlank { st.title }, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // In-Player Subtitles Drawer
        if (showSubtitlesPanel) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xF5101218))
                    .border(1.dp, accentColor.copy(alpha = 0.4f))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إعدادات الترجمة العربية", color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showSubtitlesPanel = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("حجم الخط: $subFontSizeSp", color = Color.White, fontSize = 14.sp)
                        Button(onClick = { subFontSizeSp = (subFontSizeSp - 2).coerceAtLeast(14) }) { Text("-") }
                        Button(onClick = { subFontSizeSp = (subFontSizeSp + 2).coerceAtMost(38) }) { Text("+") }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Button(
                                onClick = {
                                    selectedSubUrl = null
                                    showSubtitlesPanel = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("إيقاف الترجمة")
                            }
                        }
                        items(activeStream.subtitles) { sub ->
                            val selected = sub.url == selectedSubUrl
                            Button(
                                onClick = {
                                    selectedSubUrl = sub.url
                                    showSubtitlesPanel = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) accentColor else NuvioCard,
                                    contentColor = if (selected) Color.Black else Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(sub.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlPill(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> Color.White
                    isPrimary -> accentColor
                    else -> Color(0x33FFFFFF)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accentColor else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused || isPrimary) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (focused || isPrimary) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
