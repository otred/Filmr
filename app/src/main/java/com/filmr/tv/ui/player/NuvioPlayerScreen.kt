package com.filmr.tv.ui.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.filmr.tv.domain.model.MediaItem
import com.filmr.tv.domain.model.UnifiedStream
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioGold
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun NuvioPlayerScreen(
    item: MediaItem,
    initialStream: UnifiedStream,
    season: Int,
    episode: Int,
    epTitle: String,
    startPositionMs: Long,
    vm: FilmrViewModel
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("جاري الاتصال بالسيرفر وتجهيز البث...") }

    val player = remember {
        val headers = mutableMapOf("User-Agent" to "Mozilla/5.0 (Linux; Android TV) FilmrTV/1.1")
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
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                                statusText = "جاري تحميل البث من ${initialStream.addonName}..."
                            }
                            Player.STATE_READY -> {
                                isBuffering = false
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                            }
                            else -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = true
                        statusText = if (initialStream.isTorrent) {
                            "هذا الرابط بصيغة تورنت (Magnet) ويتطلب محرك TorrServer نشطاً للتشغيل"
                        } else {
                            "تعذّر تشغيل هذا السيرفر (${error.errorCodeName}) — جرّب سيرفراً آخر"
                        }
                    }
                })
                setMediaItem(ExoMediaItem.fromUri(Uri.parse(initialStream.url)))
                if (startPositionMs > 0) seekTo(startPositionMs)
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(item.id, season, episode) {
        val subs = vm.fetchSubtitles(item, season, episode)
        if (subs.isNotEmpty()) {
            val subConfigs = subs.take(6).map { s ->
                ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                    .setMimeType(if (s.url.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(s.lang)
                    .setLabel(s.label)
                    .setSelectionFlags(if (s.lang.startsWith("ar", true)) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            val pos = player.currentPosition
            val updated = ExoMediaItem.Builder()
                .setUri(Uri.parse(initialStream.url))
                .setSubtitleConfigurations(subConfigs)
                .build()
            player.setMediaItem(updated)
            if (pos > 0) player.seekTo(pos)
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (player.duration > 0) {
                vm.saveProgress(item, season, episode, player.currentPosition, player.duration, initialStream.url, initialStream.addonName)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (player.duration > 0) {
                vm.saveProgress(item, season, episode, player.currentPosition, player.duration, initialStream.url, initialStream.addonName)
            }
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC080A0F)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = NuvioGold, strokeWidth = 3.5.dp, modifier = Modifier.size(44.dp))
                    Text(item.displayTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(statusText, color = NuvioGold, fontSize = 14.sp)
                }
            }
        }
    }
}
