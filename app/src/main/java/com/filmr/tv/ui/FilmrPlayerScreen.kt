package com.filmr.tv.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.filmr.tv.model.MediaItem
import com.filmr.tv.model.UnifiedStream
import kotlinx.coroutines.delay

@Composable
fun FilmrPlayerScreen(
    item: MediaItem,
    initialStream: UnifiedStream,
    season: Int,
    episode: Int,
    epTitle: String,
    startPositionMs: Long,
    vm: FilmrViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(initialStream.url)))
            if (startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(item.id, season, episode) {
        val subs = vm.fetchSubtitles(item, season, episode)
        if (subs.isNotEmpty()) {
            val subConfigs = subs.take(5).map { s ->
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
        Row(
            Modifier.align(Alignment.TopStart).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvActionChip("← رجوع") { onBack() }
            Text("${item.displayTitle}${if (item.type == "series") " • S${season}E${episode}" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
