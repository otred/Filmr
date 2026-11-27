package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.data.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioBg
import com.filmr.tv.ui.components.NuvioGold
import com.filmr.tv.ui.components.NuvioNavigationRail
import com.filmr.tv.ui.player.NuvioPlayerScreen
import com.filmr.tv.ui.screens.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FilmrAppRoot()
            }
        }
    }
}

private data class ActivePlaybackRequest(
    val media: MediaItem,
    val stream: UnifiedStream,
    val subtitles: List<SubtitleTrack>,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val initialPositionMs: Long = 0L
)

@Composable
fun FilmrAppRoot(viewModel: FilmrViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var selectedPersonId by remember { mutableStateOf<Int?>(null) }
    var selectedCompany by remember { mutableStateOf<CompanyItem?>(null) }
    var playbackRequest by remember { mutableStateOf<ActivePlaybackRequest?>(null) }

    val stremioAccount by viewModel.stremioAccount.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    LaunchedEffect(Unit) {
        delay(800L)
        showSplash = false
    }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(4500L)
            viewModel.clearStatusBanner()
        }
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090D)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Filmr",
                    color = NuvioGold,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "تجربة السينما المتكاملة للتلفزيون",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    // Active Video Player takes full screen
    val activePlay = playbackRequest
    if (activePlay != null) {
        NuvioPlayerScreen(
            media = activePlay.media,
            stream = activePlay.stream,
            availableSubtitles = activePlay.subtitles,
            season = activePlay.season,
            episode = activePlay.episode,
            episodeTitle = activePlay.episodeTitle,
            initialPositionMs = activePlay.initialPositionMs,
            viewModel = viewModel,
            onBackToServers = { playbackRequest = null }
        )
        return
    }

    BackHandler(enabled = selectedPersonId != null || selectedCompany != null || selectedMedia != null) {
        when {
            selectedPersonId != null -> selectedPersonId = null
            selectedCompany != null -> selectedCompany = null
            selectedMedia != null -> selectedMedia = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left/Right TV Navigation Rail
            NuvioNavigationRail(
                selectedTab = selectedTab,
                isStremioSynced = stremioAccount.isLoggedIn,
                onSelectTab = { tab ->
                    selectedTab = tab
                    selectedMedia = null
                    selectedPersonId = null
                    selectedCompany = null
                }
            )

            // Main Content Area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    selectedPersonId != null -> {
                        NuvioPersonScreen(
                            personId = selectedPersonId!!,
                            viewModel = viewModel,
                            onBack = { selectedPersonId = null },
                            onOpenDetails = {
                                selectedPersonId = null
                                selectedMedia = it
                            }
                        )
                    }
                    selectedCompany != null -> {
                        NuvioCompanyScreen(
                            company = selectedCompany!!,
                            viewModel = viewModel,
                            onBack = { selectedCompany = null },
                            onOpenDetails = {
                                selectedCompany = null
                                selectedMedia = it
                            }
                        )
                    }
                    selectedMedia != null -> {
                        NuvioDetailsScreen(
                            initialMedia = selectedMedia!!,
                            viewModel = viewModel,
                            onBack = { selectedMedia = null },
                            onPlayStream = { media, stream, subs, s, ep, epTitle ->
                                playbackRequest = ActivePlaybackRequest(
                                    media = media,
                                    stream = stream,
                                    subtitles = subs,
                                    season = s,
                                    episode = ep,
                                    episodeTitle = epTitle
                                )
                            },
                            onOpenPerson = { selectedPersonId = it },
                            onOpenCompany = { selectedCompany = it }
                        )
                    }
                    else -> {
                        when (selectedTab) {
                            0 -> NuvioHomeScreen(
                                viewModel = viewModel,
                                onOpenDetails = { selectedMedia = it },
                                onResumeProgress = { prog ->
                                    selectedMedia = MediaItem(
                                        id = prog.mediaId,
                                        imdbId = prog.imdbId,
                                        type = prog.mediaType,
                                        titleAr = prog.title,
                                        titleEn = prog.title,
                                        posterUrl = prog.posterUrl,
                                        backdropUrl = prog.backdropUrl
                                    )
                                }
                            )
                            1 -> NuvioSearchScreen(
                                viewModel = viewModel,
                                onOpenDetails = { selectedMedia = it }
                            )
                            2 -> NuvioLibraryScreen(
                                viewModel = viewModel,
                                onOpenDetails = { selectedMedia = it }
                            )
                            3 -> NuvioSettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        // Floating Status Notification Banner
        if (!statusBanner.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B)
            ) {
                Text(
                    text = statusBanner!!,
                    color = NuvioGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
