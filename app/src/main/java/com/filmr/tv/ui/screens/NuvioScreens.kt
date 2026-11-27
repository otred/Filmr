package com.filmr.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun NuvioHomeScreen(
    viewModel: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onResumeProgress: (WatchProgressEntity) -> Unit
) {
    val catalogRows by viewModel.catalogRows.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var heroItem by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(catalogRows) {
        if (heroItem == null) {
            heroItem = catalogRows.firstOrNull()?.items?.firstOrNull()
        }
    }

    val bgColor = if (settings.amoledBlackMode) NuvioAmoledBg else NuvioBg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Panoramic Nuvio Edge-to-Edge Hero Backdrop
        val activeHero = heroItem
        if (activeHero != null && settings.showHeroBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                val bgUrl = activeHero.backdropUrl ?: activeHero.posterUrl
                if (!bgUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = bgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = 0.25f),
                                    bgColor.copy(alpha = 0.78f),
                                    bgColor
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = 0.92f),
                                    bgColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Hero Metadata Header
            item {
                if (activeHero != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NuvioGold
                            ) {
                                Text(
                                    text = if (activeHero.type == "series") "مسلسل" else "فيلم",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (activeHero.year.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = activeHero.year,
                                    color = NuvioTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val rating = activeHero.imdbRating ?: activeHero.tmdbRating
                            if (!rating.isNullOrBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = NuvioGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = rating,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isRefreshing) {
                                Spacer(Modifier.width(14.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = NuvioGold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = activeHero.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (activeHero.overview.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = activeHero.overview,
                                color = NuvioTextSecondary,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.68f)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NuvioFocusButton(
                                text = "التفاصيل والمشاهدة",
                                icon = Icons.Default.PlayArrow,
                                isPrimary = true,
                                onClick = { onOpenDetails(activeHero) }
                            )
                            NuvioFocusButton(
                                text = "تحديث القوائم",
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.refreshCatalogs() }
                            )
                        }
                    }
                }
            }

            // Continue Watching Row
            if (settings.showContinueWatching && continueWatching.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)) {
                        Text(
                            text = "متابعة المشاهدة",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(continueWatching, key = { it.key }) { prog ->
                                ContinueWatchingCard(
                                    progress = prog,
                                    lowEndMode = settings.lowEndDeviceMode,
                                    onClick = onResumeProgress
                                )
                            }
                        }
                    }
                }
            }

            // Catalog Rows (TMDB + Stremio Addons)
            items(catalogRows, key = { it.id }) { row ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = row.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(row.items, key = { "${row.id}_${it.id}" }) { media ->
                            NuvioPosterCard(
                                item = media,
                                preferEnglish = settings.preferEnglishTitles,
                                lowEndMode = settings.lowEndDeviceMode,
                                onFocused = { heroItem = it },
                                onClick = onOpenDetails
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NuvioDetailsScreen(
    initialMedia: MediaItem,
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onPlayStream: (MediaItem, UnifiedStream, List<SubtitleTrack>, Int, Int, String) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenCompany: (CompanyItem) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val library by viewModel.libraryItems.collectAsState()
    val history by viewModel.watchHistory.collectAsState()
    val streams by viewModel.streams.collectAsState()
    val isLoadingStreams by viewModel.isLoadingStreams.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()

    val scope = rememberCoroutineScope()
    var media by remember { mutableStateOf(initialMedia) }
    var isEnriching by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var showServerPicker by remember { mutableStateOf(false) }
    var activeSeasonForStream by remember { mutableIntStateOf(0) }
    var activeEpisodeForStream by remember { mutableIntStateOf(0) }
    var activeEpisodeTitle by remember { mutableStateOf("") }

    LaunchedEffect(initialMedia.id) {
        isEnriching = true
        val enriched = viewModel.enrichDetails(initialMedia)
        media = enriched
        episodes = enriched.episodes
        isEnriching = false
    }

    val isInLibrary = library.any { it.mediaId == media.id }
    val isWatched = history.any { it.mediaId == media.id }
    val bgColor = if (settings.amoledBlackMode) NuvioAmoledBg else NuvioBg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Backdrop
        val bgUrl = media.backdropUrl ?: media.posterUrl
        if (!bgUrl.isNullOrBlank()) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.35f),
                                bgColor.copy(alpha = 0.88f),
                                bgColor
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Info + Ratings + Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Poster
                    Box(
                        modifier = Modifier
                            .width(170.dp)
                            .height(250.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NuvioCardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    ) {
                        if (!media.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = media.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (media.titleEn.isNotBlank() && media.titleEn != media.displayTitle(settings.preferEnglishTitles)) {
                            Text(
                                text = media.titleEn,
                                color = NuvioTextSecondary,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Multi-Ratings Bar (Nuvio Style)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (settings.showImdbRating && !media.imdbRating.isNullOrBlank()) {
                                RatingBadge("IMDb", media.imdbRating!!, Color(0xFFF5C518))
                            }
                            if (settings.showTmdbRating && !media.tmdbRating.isNullOrBlank()) {
                                RatingBadge("TMDB", media.tmdbRating!!, Color(0xFF01B4E4))
                            }
                            if (settings.showRtRating && !media.rtRating.isNullOrBlank()) {
                                RatingBadge("RT", media.rtRating!!, Color(0xFFFA320A))
                            }
                            if (settings.showMetacriticRating && !media.metacriticRating.isNullOrBlank()) {
                                RatingBadge("MC", media.metacriticRating!!, Color(0xFF66CC33))
                            }
                            if (settings.showLetterboxdRating && !media.letterboxdRating.isNullOrBlank()) {
                                RatingBadge("LB", media.letterboxdRating!!, Color(0xFF00E054))
                            }
                            if (settings.showMalRating && !media.malRating.isNullOrBlank()) {
                                RatingBadge("MAL", media.malRating!!, Color(0xFF2E51A2))
                            }
                            if (media.year.isNotBlank()) {
                                Text(text = "• ${media.year}", color = NuvioTextSecondary, fontSize = 13.sp)
                            }
                            if (media.runtimeMinutes > 0) {
                                Text(text = "• ${media.runtimeMinutes} د", color = NuvioTextSecondary, fontSize = 13.sp)
                            }
                        }

                        if (media.genres.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = media.genres.joinToString(" • "),
                                color = NuvioGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = media.overview.ifBlank { "لا يوجد وصف متاح حالياً." },
                            color = NuvioTextPrimary,
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(16.dp))

                        // Action Buttons Row
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NuvioFocusButton(
                                text = if (media.type == "series") "اختر الحلقة والمصادر" else "تشغيل (عرض السيرفرات)",
                                icon = Icons.Default.PlayArrow,
                                isPrimary = true,
                                onClick = {
                                    if (media.type == "series" && episodes.isNotEmpty()) {
                                        val firstEp = episodes.first()
                                        activeSeasonForStream = firstEp.season
                                        activeEpisodeForStream = firstEp.episode
                                        activeEpisodeTitle = firstEp.title
                                        viewModel.loadStreamsAndSubtitles(media, firstEp.season, firstEp.episode)
                                    } else {
                                        activeSeasonForStream = 0
                                        activeEpisodeForStream = 0
                                        activeEpisodeTitle = ""
                                        viewModel.loadStreamsAndSubtitles(media)
                                    }
                                    showServerPicker = true
                                }
                            )

                            NuvioFocusButton(
                                text = if (isInLibrary) "في المكتبة ✓" else "أضف للمكتبة",
                                icon = if (isInLibrary) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                onClick = { viewModel.toggleLibrary(media) }
                            )

                            NuvioFocusButton(
                                text = if (isWatched) "تمت المشاهدة ✓" else "تحديد كمشاهد",
                                icon = Icons.Default.CheckCircle,
                                onClick = { viewModel.toggleWatched(media) }
                            )

                            NuvioFocusButton(
                                text = "رجوع",
                                icon = Icons.Default.ArrowBack,
                                onClick = onBack
                            )
                        }
                    }
                }
            }

            // Seasons & Episodes (for Series)
            if (media.type == "series" && media.seasonsCount > 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "المواسم والحلقات",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((1..media.seasonsCount).toList()) { sNum ->
                                NuvioChip(
                                    label = "الموسم $sNum",
                                    selected = selectedSeason == sNum,
                                    onClick = {
                                        selectedSeason = sNum
                                        val tId = media.tmdbId
                                        if (tId != null) {
                                            scope.launch {
                                                episodes = viewModel.loadSeasonEpisodes(tId, sNum, media.imdbId ?: media.id)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(episodes, key = { it.id }) { ep ->
                                EpisodeCard(
                                    episode = ep,
                                    onClick = {
                                        activeSeasonForStream = ep.season
                                        activeEpisodeForStream = ep.episode
                                        activeEpisodeTitle = ep.title
                                        viewModel.loadStreamsAndSubtitles(media, ep.season, ep.episode)
                                        showServerPicker = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Cast Row
            if (media.cast.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "طاقم التمثيل",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(media.cast, key = { it.id }) { actor ->
                                CastCard(actor = actor, onClick = { onOpenPerson(actor.id) })
                            }
                        }
                    }
                }
            }

            // Companies & Networks
            if (media.companies.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "الشبكات وجهات الإنتاج",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(media.companies, key = { "${it.isNetwork}_${it.id}" }) { comp ->
                                NuvioChip(
                                    label = comp.name,
                                    selected = false,
                                    icon = if (comp.isNetwork) Icons.Default.LiveTv else Icons.Default.Business,
                                    onClick = { onOpenCompany(comp) }
                                )
                            }
                        }
                    }
                }
            }

            // Recommendations
            if (media.recommendations.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "أعمال مشابهة قد تعجبك",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(media.recommendations, key = { "rec_${it.id}" }) { rec ->
                                NuvioPosterCard(
                                    item = rec,
                                    preferEnglish = settings.preferEnglishTitles,
                                    lowEndMode = settings.lowEndDeviceMode,
                                    onClick = {
                                        media = it
                                        scope.launch {
                                            val enr = viewModel.enrichDetails(it)
                                            media = enr
                                            episodes = enr.episodes
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // NUVIO-STYLE UNIFIED SERVER PICKER OVERLAY (Stremio + CloudStream + Built-in Torrent Tabs)
        if (showServerPicker) {
            NuvioServerPickerOverlay(
                mediaTitle = media.displayTitle(settings.preferEnglishTitles),
                episodeLabel = if (activeSeasonForStream > 0) "الموسم $activeSeasonForStream • الحلقة $activeEpisodeForStream" else "",
                streams = streams,
                isLoading = isLoadingStreams,
                onDismiss = { showServerPicker = false },
                onSelectStream = { selectedStream ->
                    showServerPicker = false
                    onPlayStream(
                        media,
                        selectedStream,
                        subtitles,
                        activeSeasonForStream,
                        activeEpisodeForStream,
                        activeEpisodeTitle
                    )
                }
            )
        }
    }
}

@Composable
private fun RatingBadge(label: String, score: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(NuvioCardBg)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(4.dp))
        Text(text = score, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EpisodeCard(episode: EpisodeItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NuvioCardBg)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .background(Color.Black)
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = "الحلقة ${episode.episode}",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(NuvioGold)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = episode.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    color = NuvioTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CastCard(actor: CastMember, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(105.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(NuvioCardBg)
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!actor.profileUrl.isNullOrBlank()) {
                AsyncImage(
                    model = actor.profileUrl,
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = NuvioTextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = actor.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actor.character.isNotBlank()) {
            Text(
                text = actor.character,
                color = NuvioTextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Nuvio-Style Server Picker Modal:
 * Displays tabbed filtering by Addon/Engine (الكل، إضافات Stremio، إضافات CloudStream، تورنت مدمج، + كل إضافة على حدة)
 * so the user sees all available streams and chooses the server they prefer!
 */
@Composable
fun NuvioServerPickerOverlay(
    mediaTitle: String,
    episodeLabel: String,
    streams: List<UnifiedStream>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectStream: (UnifiedStream) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val addonTabs = remember(streams) {
        streams.map { it.addonName }.distinct()
    }

    val filteredStreams = remember(streams, selectedFilter) {
        when (selectedFilter) {
            "ALL" -> streams
            "STREMIO" -> streams.filter { it.sourceEngine == "stremio" || (it.sourceEngine == "torrent" && !it.id.contains("_cs_")) }
            "CLOUDSTREAM" -> streams.filter { it.sourceEngine == "cloudstream" }
            "TORRENT" -> streams.filter { it.isTorrent }
            else -> streams.filter { it.addonName == selectedFilter }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "اختر السيرفر ومصدر التشغيل",
                            color = NuvioGold,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (episodeLabel.isNotBlank()) "$mediaTitle • $episodeLabel" else mediaTitle,
                            color = NuvioTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    NuvioFocusButton(
                        text = "إغلاق",
                        icon = Icons.Default.Close,
                        onClick = onDismiss
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Categorized Tabs (All, Stremio, CloudStream, Torrent, Individual Addons)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        NuvioChip(
                            label = "الكل (${streams.size})",
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" }
                        )
                    }
                    item {
                        val count = streams.count { it.sourceEngine != "cloudstream" }
                        NuvioChip(
                            label = "Stremio ($count)",
                            selected = selectedFilter == "STREMIO",
                            onClick = { selectedFilter = "STREMIO" }
                        )
                    }
                    item {
                        val count = streams.count { it.sourceEngine == "cloudstream" }
                        NuvioChip(
                            label = "CloudStream ($count)",
                            selected = selectedFilter == "CLOUDSTREAM",
                            onClick = { selectedFilter = "CLOUDSTREAM" }
                        )
                    }
                    item {
                        val count = streams.count { it.isTorrent }
                        NuvioChip(
                            label = "تورنت مدمج ($count)",
                            selected = selectedFilter == "TORRENT",
                            onClick = { selectedFilter = "TORRENT" }
                        )
                    }
                    items(addonTabs) { addonName ->
                        NuvioChip(
                            label = addonName,
                            selected = selectedFilter == addonName,
                            onClick = { selectedFilter = addonName }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (isLoading && streams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NuvioGold)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "جاري جلب الروابط من إضافات Stremio و CloudStream...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (filteredStreams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد روابط متاحة في هذا التصنيف حالياً. جرّب تفعيل إضافات إضافية من الإعدادات.",
                            color = NuvioTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredStreams, key = { it.id }) { stream ->
                            StreamServerRow(stream = stream, onClick = { onSelectStream(stream) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamServerRow(stream: UnifiedStream, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) Color.White.copy(alpha = 0.16f) else NuvioCardBg,
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quality & Engine Badge
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stream.quality,
                    color = NuvioGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        stream.isTorrent -> "TORRENT"
                        stream.sourceEngine == "cloudstream" -> "CLOUD"
                        else -> "DIRECT"
                    },
                    color = when {
                        stream.isTorrent -> Color(0xFF38BDF8)
                        stream.sourceEngine == "cloudstream" -> Color(0xFFA855F7)
                        else -> Color(0xFF22C55E)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stream.addonName,
                        color = NuvioGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (stream.sizeText.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "💾 ${stream.sizeText}",
                            color = NuvioTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (stream.seeders > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "👤 ${stream.seeders}",
                            color = Color(0xFF22C55E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stream.description.ifBlank { stream.title },
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = "تشغيل",
                tint = if (focused) NuvioGold else Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun NuvioSearchScreen(
    viewModel: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit
) {
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var query by remember { mutableStateOf("") }
    val bgColor = if (settings.amoledBlackMode) NuvioAmoledBg else NuvioBg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(28.dp)
    ) {
        Text(
            text = "البحث الشامل (TMDB + الإضافات)",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                NuvioWhiteTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "اكتب اسم الفيلم أو المسلسل بالعربية أو الإنجليزية..."
                )
            }
            NuvioFocusButton(
                text = "بحث",
                icon = Icons.Default.Search,
                isPrimary = true,
                onClick = { viewModel.searchMedia(query) }
            )
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.query }) { item ->
                    NuvioChip(
                        label = item.query,
                        selected = false,
                        icon = Icons.Default.History,
                        onClick = {
                            query = item.query
                            viewModel.searchMedia(item.query)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NuvioGold)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { "${it.type}_${it.id}" }) { item ->
                    NuvioPosterCard(
                        item = item,
                        preferEnglish = settings.preferEnglishTitles,
                        lowEndMode = settings.lowEndDeviceMode,
                        onClick = onOpenDetails
                    )
                }
            }
        }
    }
}

@Composable
fun NuvioLibraryScreen(
    viewModel: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit
) {
    val library by viewModel.libraryItems.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val stremioAccount by viewModel.stremioAccount.collectAsState()
    val bgColor = if (settings.amoledBlackMode) NuvioAmoledBg else NuvioBg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "مكتبتي (${library.size})",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                if (stremioAccount.isLoggedIn) {
                    Text(
                        text = "متزامنة تلقائياً مع حساب Stremio: ${stremioAccount.email}",
                        color = Color(0xFF22C55E),
                        fontSize = 12.sp
                    )
                }
            }
            if (stremioAccount.isLoggedIn) {
                NuvioFocusButton(
                    text = "مزامنة فورية الآن",
                    icon = Icons.Default.Sync,
                    isPrimary = true,
                    onClick = { viewModel.syncNowStremioAccount() }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (library.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "مكتبتك فارغة حالياً. أضف أعمالاً من صفحة التفاصيل أو قم بمزامنة حساب Stremio.",
                    color = NuvioTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(library, key = { it.mediaId }) { lib ->
                    val mediaItem = MediaItem(
                        id = lib.mediaId,
                        imdbId = lib.imdbId,
                        type = lib.mediaType,
                        titleAr = lib.titleAr,
                        titleEn = lib.titleEn,
                        posterUrl = lib.posterUrl,
                        backdropUrl = lib.backdropUrl,
                        year = lib.year,
                        imdbRating = lib.rating
                    )
                    NuvioPosterCard(
                        item = mediaItem,
                        preferEnglish = settings.preferEnglishTitles,
                        lowEndMode = settings.lowEndDeviceMode,
                        onClick = onOpenDetails
                    )
                }
            }
        }
    }
}

@Composable
fun NuvioSettingsScreen(viewModel: FilmrViewModel) {
    val settings by viewModel.settings.collectAsState()
    val stremioAccount by viewModel.stremioAccount.collectAsState()
    val allAddons by viewModel.allAddons.collectAsState()

    var activeSection by remember { mutableIntStateOf(0) }
    var stremioEmail by remember { mutableStateOf("") }
    var stremioPass by remember { mutableStateOf("") }
    var stremioAddonUrl by remember { mutableStateOf("") }
    var cloudStreamRepoUrl by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    val stremioAddons = remember(allAddons) { allAddons.filter { it.sourceType == "stremio" } }
    val cloudStreamAddons = remember(allAddons) { allAddons.filter { it.sourceType == "cloudstream" } }
    val bgColor = if (settings.amoledBlackMode) NuvioAmoledBg else NuvioBg

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(28.dp)
    ) {
        Text(
            text = "إعدادات Filmr TV",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(14.dp))

        // Settings Category Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioChip(
                label = "مزامنة حساب Stremio",
                selected = activeSection == 0,
                icon = Icons.Default.CloudSync,
                onClick = { activeSection = 0 }
            )
            NuvioChip(
                label = "إضافات Stremio (${stremioAddons.size})",
                selected = activeSection == 1,
                icon = Icons.Default.Extension,
                onClick = { activeSection = 1 }
            )
            NuvioChip(
                label = "مستودعات CloudStream (${cloudStreamAddons.size})",
                selected = activeSection == 2,
                icon = Icons.Default.Hub,
                onClick = { activeSection = 2 }
            )
            NuvioChip(
                label = "الأداء والمظهر والترجمة",
                selected = activeSection == 3,
                icon = Icons.Default.Tune,
                onClick = { activeSection = 3 }
            )
        }

        if (!feedbackMsg.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(text = feedbackMsg!!, color = NuvioGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            when (activeSection) {
                // SECTION 0: Stremio Account Fast Sync
                0 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NuvioSurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "مزامنة حساب Stremio الفورية",
                                    color = NuvioGold,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "سجّل الدخول بحسابك في Stremio ليتم تثبيت جميع إضافاتك ومزامنة مكتبتك وسجل متابعة المشاهدة تلقائياً وبسرعة فائقة.",
                                    color = NuvioTextSecondary,
                                    fontSize = 13.sp
                                )

                                if (stremioAccount.isLoggedIn) {
                                    Text(
                                        text = "الحساب المتصل: ${stremioAccount.email}",
                                        color = Color(0xFF22C55E),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• الإضافات المتزامنة: ${stremioAccount.syncedAddonsCount}   • عناصر المكتبة: ${stremioAccount.syncedLibraryCount}   • سجل المشاهدة: ${stremioAccount.syncedHistoryCount}",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        NuvioFocusButton(
                                            text = "مزامنة سريعة الآن",
                                            icon = Icons.Default.Sync,
                                            isPrimary = true,
                                            onClick = { viewModel.syncNowStremioAccount() }
                                        )
                                        NuvioFocusButton(
                                            text = "تسجيل الخروج",
                                            icon = Icons.Default.Logout,
                                            onClick = { viewModel.logoutStremioAccount() }
                                        )
                                    }
                                } else {
                                    NuvioWhiteTextField(
                                        value = stremioEmail,
                                        onValueChange = { stremioEmail = it },
                                        placeholder = "البريد الإلكتروني لحساب Stremio"
                                    )
                                    NuvioWhiteTextField(
                                        value = stremioPass,
                                        onValueChange = { stremioPass = it },
                                        placeholder = "كلمة المرور",
                                        isPassword = true
                                    )
                                    NuvioFocusButton(
                                        text = "تسجيل الدخول ومزامنة كل شيء فوراً",
                                        icon = Icons.Default.CloudSync,
                                        isPrimary = true,
                                        onClick = {
                                            viewModel.loginAndSyncStremio(stremioEmail, stremioPass) { _, msg ->
                                                feedbackMsg = msg
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 1: Separate Stremio Addons Manager
                1 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NuvioSurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "تثبيت إضافة Stremio يدوياً (manifest.json)",
                                    color = NuvioGold,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                NuvioWhiteTextField(
                                    value = stremioAddonUrl,
                                    onValueChange = { stremioAddonUrl = it },
                                    placeholder = "https://.../manifest.json"
                                )
                                NuvioFocusButton(
                                    text = "تثبيت إضافة Stremio",
                                    icon = Icons.Default.AddCircle,
                                    isPrimary = true,
                                    onClick = {
                                        viewModel.installStremioAddon(stremioAddonUrl) { ok, msg ->
                                            feedbackMsg = msg
                                            if (ok) stremioAddonUrl = ""
                                        }
                                    }
                                )
                            }
                        }
                    }

                    items(stremioAddons, key = { it.id }) { addon ->
                        AddonManageCard(
                            addon = addon,
                            badgeText = if (addon.isFromStremioAccount) "حساب Stremio" else "Stremio",
                            onToggle = { viewModel.toggleAddon(addon.id, !addon.enabled) },
                            onDelete = { viewModel.removeAddon(addon.id) }
                        )
                    }
                }

                // SECTION 2: Completely Separate CloudStream Repo & Manual Plugin Installer
                2 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NuvioSurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "مستودعات وإضافات CloudStream (قسم مستقل)",
                                    color = Color(0xFFA855F7),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "أضف رابط مستودع CloudStream (repo.json أو plugins.json) وسيتم دمج سيرفراته تلقائياً داخل قائمة السيرفرات عند تشغيل أي فيلم أو مسلسل.",
                                    color = NuvioTextSecondary,
                                    fontSize = 12.sp
                                )
                                NuvioWhiteTextField(
                                    value = cloudStreamRepoUrl,
                                    onValueChange = { cloudStreamRepoUrl = it },
                                    placeholder = "رابط مستودع أو إضافة CloudStream (.json)"
                                )
                                NuvioFocusButton(
                                    text = "تثبيت مستودع CloudStream",
                                    icon = Icons.Default.AddLink,
                                    isPrimary = true,
                                    onClick = {
                                        viewModel.installCloudStreamRepo(cloudStreamRepoUrl) { ok, msg ->
                                            feedbackMsg = msg
                                            if (ok) cloudStreamRepoUrl = ""
                                        }
                                    }
                                )
                            }
                        }
                    }

                    items(cloudStreamAddons, key = { it.id }) { plugin ->
                        AddonManageCard(
                            addon = plugin,
                            badgeText = "CloudStream",
                            onToggle = { viewModel.toggleAddon(plugin.id, !plugin.enabled) },
                            onDelete = { viewModel.removeAddon(plugin.id) }
                        )
                    }
                }

                // SECTION 3: Performance (Low-End vs High-End), Nuvio Appearance & Subtitle Studio
                3 -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsToggleRow(
                                title = "وضع الأجهزة الضعيفة والخفيفة (Low-End Performance Mode)",
                                subtitle = "يقلل استهلاك الذاكرة والرسوميات لتشغيل فائق السرعة على أجهزة TV Box الضعيفة",
                                checked = settings.lowEndDeviceMode,
                                onToggle = { viewModel.updateSettings { it.copy(lowEndDeviceMode = !it.lowEndDeviceMode) } }
                            )
                            SettingsToggleRow(
                                title = "وضع AMOLED الأسود بالكامل",
                                subtitle = "خلفية سوداء نقية توفر الطاقة وتمنح تبايناً سينمائياً",
                                checked = settings.amoledBlackMode,
                                onToggle = { viewModel.updateSettings { it.copy(amoledBlackMode = !it.amoledBlackMode) } }
                            )
                            SettingsToggleRow(
                                title = "تفضيل العناوين الإنجليزية",
                                subtitle = "عرض الاسم الإنجليزي للأفلام والمسلسلات بدلاً من المعرّب",
                                checked = settings.preferEnglishTitles,
                                onToggle = { viewModel.updateSettings { it.copy(preferEnglishTitles = !it.preferEnglishTitles) } }
                            )
                            SettingsToggleRow(
                                title = "إظهار صف متابعة المشاهدة",
                                subtitle = "عرض الأعمال التي لم تكمل مشاهدتها في الشاشة الرئيسية",
                                checked = settings.showContinueWatching,
                                onToggle = { viewModel.updateSettings { it.copy(showContinueWatching = !it.showContinueWatching) } }
                            )
                            SettingsToggleRow(
                                title = "التشغيل التلقائي للحلقة التالية",
                                subtitle = "الانتقال التلقائي للحلقة التالية عند انتهاء الحلقة الحالية",
                                checked = settings.autoPlayNextEpisode,
                                onToggle = { viewModel.updateSettings { it.copy(autoPlayNextEpisode = !it.autoPlayNextEpisode) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonManageCard(
    addon: AddonEntity,
    badgeText: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NuvioCardBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = addon.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = badgeText,
                        color = NuvioGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (addon.description.isNotBlank()) {
                    Text(
                        text = addon.description,
                        color = NuvioTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NuvioChip(
                    label = if (addon.enabled) "مفعّلة" else "معطّلة",
                    selected = addon.enabled,
                    onClick = onToggle
                )
                NuvioChip(
                    label = "حذف",
                    selected = false,
                    icon = Icons.Default.Delete,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) Color.White.copy(alpha = 0.14f) else NuvioCardBg,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = NuvioTextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NuvioGold)
            )
        }
    }
}

@Composable
fun NuvioPersonScreen(
    personId: Int,
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var person by remember { mutableStateOf<PersonDetails?>(null) }

    LaunchedEffect(personId) {
        person = viewModel.loadPersonDetails(personId)
    }

    val p = person
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(28.dp)
    ) {
        NuvioFocusButton(text = "رجوع", icon = Icons.Default.ArrowBack, onClick = onBack)
        Spacer(Modifier.height(16.dp))
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NuvioGold)
            }
        } else {
            Text(text = p.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            if (p.biography.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = p.biography, color = NuvioTextSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(p.credits, key = { "p_${it.id}" }) { item ->
                    NuvioPosterCard(
                        item = item,
                        preferEnglish = settings.preferEnglishTitles,
                        lowEndMode = settings.lowEndDeviceMode,
                        onClick = onOpenDetails
                    )
                }
            }
        }
    }
}

@Composable
fun NuvioCompanyScreen(
    company: CompanyItem,
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var itemsList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var sortBy by remember { mutableStateOf("popularity.desc") }

    LaunchedEffect(company.id, sortBy) {
        itemsList = viewModel.discoverCompany(company.id, company.isNetwork, sortBy)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(28.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioFocusButton(text = "رجوع", icon = Icons.Default.ArrowBack, onClick = onBack)
            Text(text = company.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NuvioChip("الأكثر رواجاً", sortBy == "popularity.desc") { sortBy = "popularity.desc" }
            NuvioChip("الأعلى تقييماً", sortBy == "vote_average.desc") { sortBy = "vote_average.desc" }
            NuvioChip("الأحدث", sortBy == "primary_release_date.desc") { sortBy = "primary_release_date.desc" }
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(itemsList, key = { "c_${it.id}" }) { item ->
                NuvioPosterCard(
                    item = item,
                    preferEnglish = settings.preferEnglishTitles,
                    lowEndMode = settings.lowEndDeviceMode,
                    onClick = onOpenDetails
                )
            }
        }
    }
}
