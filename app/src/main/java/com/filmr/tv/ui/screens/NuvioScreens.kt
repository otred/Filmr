package com.filmr.tv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.*

@Composable
fun NuvioHomeScreen(
    viewModel: FilmrViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onResumeProgress: (WatchProgressEntity) -> Unit
) {
    val rows by viewModel.catalogRows.collectAsState()
    val progressList by viewModel.watchProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var drawerOpen by remember { mutableStateOf(false) }
    val drawerFocusRequester = remember { FocusRequester() }

    var heroItem by remember { mutableStateOf<MediaItem?>(null) }
    var translatedHeroOverview by remember { mutableStateOf("") }

    LaunchedEffect(rows) {
        if (heroItem == null) {
            heroItem = rows.firstOrNull()?.items?.firstOrNull()
        }
    }

    LaunchedEffect(heroItem) {
        val raw = heroItem?.overviewAr ?: ""
        translatedHeroOverview = raw
        if (raw.isNotBlank()) {
            viewModel.translateTextForHero(raw) { arText ->
                translatedHeroOverview = arText
            }
        }
    }

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            try {
                drawerFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val bgColor = if (settings.amoledDarkMode) Color.Black else NuvioBg

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        heroItem?.let { hero ->
            AsyncImage(
                model = hero.backdropUrl.ifBlank { hero.posterUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.92f),
                                bgColor.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.25f),
                                bgColor.copy(alpha = 0.72f),
                                bgColor
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Hero Banner Info (Fixed crisp area at top like NuvioTV)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
                    .padding(start = 28.dp, end = 28.dp, top = 20.dp)
            ) {
                heroItem?.let { hero ->
                    Column(modifier = Modifier.fillMaxWidth(0.62f)) {
                        if (hero.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = hero.logoUrl,
                                contentDescription = hero.displayTitle(settings.preferEnglishTitles),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.height(64.dp).fillMaxWidth(0.75f)
                            )
                        } else {
                            Text(
                                text = hero.displayTitle(settings.preferEnglishTitles),
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (hero.type == "series") "مسلسل" else "فيلم",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (hero.year.isNotBlank()) {
                                Text("• ${hero.year}", color = Color.LightGray, fontSize = 12.sp)
                            }
                            val score = hero.imdbRating.ifBlank { hero.tmdbRating }
                            if (score.isNotBlank() && score != "0.0") {
                                Box(
                                    modifier = Modifier
                                        .background(NuvioGold, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("IMDb $score", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        if (translatedHeroOverview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = translatedHeroOverview,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Scrollable Rows
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (settings.showContinueWatching && progressList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = "متابعة المشاهدة",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(progressList) { idx, prog ->
                                    ContinueWatchingCard(
                                        progress = prog,
                                        isFirstInRow = idx == 0,
                                        onRequestOpenDrawer = { drawerOpen = true },
                                        onClick = { onResumeProgress(it) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (rows.isEmpty() && isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                items(rows) { row ->
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(
                            text = row.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            itemsIndexed(row.items) { idx, item ->
                                NuvioPosterCard(
                                    item = item,
                                    preferEnglish = settings.preferEnglishTitles,
                                    isFirstInRow = idx == 0,
                                    onRequestOpenDrawer = { drawerOpen = true },
                                    onFocusItem = { heroItem = it },
                                    onClick = { onOpenDetails(it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        NuvioSideDrawer(
            visible = drawerOpen,
            currentRoute = "home",
            drawerFocusRequester = drawerFocusRequester,
            onSelectRoute = onNavigate,
            onDismiss = { drawerOpen = false }
        )
    }
}

@Composable
fun NuvioDetailsScreen(
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onPlayStream: (MediaItem, UnifiedStream, Int, Int) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenCompany: (CompanyItem) -> Unit
) {
    val context = LocalContext.current
    val item by viewModel.selectedItem.collectAsState()
    val streams by viewModel.streams.collectAsState()
    val isLoadingStreams by viewModel.isLoadingStreams.collectAsState()
    val library by viewModel.library.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var showStreamsDrawer by remember { mutableStateOf(false) }
    var selectedAddonTab by remember { mutableStateOf("الكل") }
    var bottomTab by remember { mutableStateOf("cast") }
    val playBtnFocus = remember { FocusRequester() }

    val media = item ?: return
    val libEntry = library.find { it.mediaId == media.id }
    val inLibrary = libEntry != null
    val isWatched = libEntry?.isWatched == true
    val bgColor = if (settings.amoledDarkMode) Color.Black else NuvioBg

    LaunchedEffect(media.id) {
        try {
            playBtnFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        AsyncImage(
            model = media.backdropUrl.ifBlank { media.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            bgColor.copy(alpha = 0.94f),
                            bgColor.copy(alpha = 0.72f),
                            bgColor.copy(alpha = 0.25f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bgColor.copy(alpha = 0.85f), bgColor)
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth(0.68f)) {
                    if (media.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = media.logoUrl,
                            contentDescription = media.displayTitle(settings.preferEnglishTitles),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(78.dp).fillMaxWidth(0.8f)
                        )
                    } else {
                        Text(
                            text = media.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    if (media.director.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المخرج: ${media.director}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row (Play, Add to Library, Mark Watched, Trailer)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NuvioChip(
                            label = if (media.type == "series") "تشغيل م${selectedSeason}:ح${selectedEpisode}" else "تشغيل",
                            selected = true,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.focusRequester(playBtnFocus)
                        ) {
                            selectedAddonTab = "الكل"
                            showStreamsDrawer = true
                            viewModel.loadStreamsForItem(media, selectedSeason, selectedEpisode)
                        }

                        NuvioCircleButton(
                            icon = if (inLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "إضافة للمكتبة",
                            active = inLibrary
                        ) {
                            viewModel.toggleLibrary(media)
                        }

                        NuvioCircleButton(
                            icon = Icons.Default.Visibility,
                            contentDescription = "تحديد كمشاهد",
                            active = isWatched
                        ) {
                            viewModel.toggleWatched(media)
                        }

                        if (media.trailerKey.isNotBlank()) {
                            NuvioCircleButton(
                                icon = Icons.Default.Movie,
                                contentDescription = "الإعلان التشويقي"
                            ) {
                                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${media.trailerKey}"))
                                context.startActivity(ytIntent)
                            }
                        }
                    }

                    statusMsg?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = NuvioGold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-source Ratings Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (settings.showImdbRating && media.imdbRating.isNotBlank() && media.imdbRating != "0.0") {
                            RatingBadge("IMDb", media.imdbRating, NuvioGold, Color.Black)
                        }
                        if (settings.showTmdbRating && media.tmdbRating.isNotBlank()) {
                            RatingBadge("TMDB", media.tmdbRating, Color(0xFF0EA5E9), Color.White)
                        }
                        if (settings.showRtRating && media.rtRating.isNotBlank()) {
                            RatingBadge("RT", media.rtRating, Color(0xFFEF4444), Color.White)
                        }
                        if (settings.showAudienceRating && media.audienceRating.isNotBlank()) {
                            RatingBadge("Popcorn", media.audienceRating, Color(0xFFF97316), Color.White)
                        }
                        if (settings.showMcRating && media.metacriticRating.isNotBlank()) {
                            RatingBadge("MC", media.metacriticRating, Color(0xFF22C55E), Color.Black)
                        }
                        if (settings.showLbRating && media.letterboxdRating.isNotBlank()) {
                            RatingBadge("LB", media.letterboxdRating, Color(0xFF10B981), Color.Black)
                        }
                        if (settings.showMalRating && media.malRating.isNotBlank()) {
                            RatingBadge("MAL", media.malRating, Color(0xFF3B82F6), Color.White)
                        }
                    }

                    if (media.overviewAr.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = media.overviewAr,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Genres & Metadata chips
                    val metaParts = buildList {
                        if (media.genres.isNotEmpty()) add(media.genres.joinToString(" • "))
                        if (media.releaseDate.isNotBlank()) add(media.releaseDate)
                        val rt = media.formattedRuntime()
                        if (rt.isNotBlank()) add(rt)
                        if (media.originalLang.isNotBlank()) add(media.originalLang)
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString("  |  "),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Series Seasons & Episodes
            if (media.type == "series") {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..media.seasonsCount).toList()) { sNum ->
                            NuvioChip("الموسم $sNum", selected = selectedSeason == sNum) {
                                selectedSeason = sNum
                                selectedEpisode = 1
                                viewModel.loadSeasonEpisodes(sNum)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(media.episodes) { ep ->
                            var epFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .width(230.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NuvioCardBg)
                                    .border(
                                        width = if (epFocused) 2.5.dp else 1.dp,
                                        color = if (epFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { epFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        selectedEpisode = ep.episodeNumber
                                        selectedAddonTab = "الكل"
                                        showStreamsDrawer = true
                                        viewModel.loadStreamsForItem(media, selectedSeason, ep.episodeNumber)
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(118.dp).background(Color(0xFF121217))) {
                                    AsyncImage(
                                        model = ep.stillUrl.ifBlank { media.backdropUrl },
                                        contentDescription = ep.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("الحلقة ${ep.episodeNumber}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = ep.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ep.overview.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = ep.overview,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Tabs: Cast & Crew | Similar | Companies & Networks
            item {
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NuvioChip("المؤلف وطاقم العمل", selected = bottomTab == "cast") { bottomTab = "cast" }
                    if (media.recommendations.isNotEmpty()) {
                        NuvioChip("مقترحات مشابهة", selected = bottomTab == "similar") { bottomTab = "similar" }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (bottomTab == "cast" && media.cast.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(media.cast) { actor ->
                            var focused by remember { mutableStateOf(false) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(92.dp)
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clickable { onOpenPerson(actor.id) }
                            ) {
                                AsyncImage(
                                    model = actor.profileUrl,
                                    contentDescription = actor.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(66.dp)
                                        .clip(CircleShape)
                                        .background(NuvioCardBg)
                                        .border(
                                            width = if (focused) 2.5.dp else 1.dp,
                                            color = if (focused) Color.White else Color.White.copy(alpha = 0.16f),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(actor.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (actor.character.isNotBlank()) {
                                    Text(actor.character, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else if (bottomTab == "similar") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(media.recommendations) { rec ->
                            NuvioPosterCard(rec, settings.preferEnglishTitles) { viewModel.openDetails(it) }
                        }
                    }
                }
            }

            if (media.companies.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("جهة الإنتاج وشبكة العرض", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(media.companies) { comp ->
                            NuvioChip(comp.name) { onOpenCompany(comp) }
                        }
                    }
                }
            }
        }

        // Left-side Nuvio Stream Selector Overlay (Backdrop & Title stay visible on right!)
        if (showStreamsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Back) {
                            showStreamsDrawer = false
                            true
                        } else false
                    }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.46f)
                        .background(Color(0xFF111116).copy(alpha = 0.96f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر السيرفر", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        NuvioCircleButton(Icons.Default.Close, "إغلاق") { showStreamsDrawer = false }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val addonNames = listOf("الكل") + streams.map { it.addonName }.distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(addonNames) { tab ->
                            NuvioChip(tab, selected = selectedAddonTab == tab) {
                                selectedAddonTab = tab
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingStreams) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("جاري جلب السيرفرات من الإضافات...", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        val filtered = if (selectedAddonTab == "الكل") streams else streams.filter { it.addonName == selectedAddonTab }
                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "لا توجد روابط متاحة حالياً.
تأكد من تثبيت إضافة تورنت (مثل Torrentio) في الإعدادات.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(filtered) { st ->
                                    var sFocused by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sFocused) Color.White.copy(alpha = 0.18f) else NuvioCardBg)
                                            .border(
                                                width = if (sFocused) 2.dp else 1.dp,
                                                color = if (sFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .onFocusChanged { sFocused = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                showStreamsDrawer = false
                                                onPlayStream(media, st, selectedSeason, selectedEpisode)
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .background(NuvioGold, RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(st.quality, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = st.addonName,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            if (st.isTorrent) {
                                                Text("P2P تورنت", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = st.title,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
}

@Composable
private fun RatingBadge(label: String, value: String, bg: Color, fg: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text("$label ", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(value, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NuvioSearchScreen(
    viewModel: FilmrViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerFocus = remember { FocusRequester() }

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            try { drawerFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            ) {
                NuvioCircleButton(Icons.Default.Menu, "القائمة") { drawerOpen = true }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن فيلم أو مسلسل أو أنمي...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                NuvioChip("بحث", selected = true, icon = Icons.Default.Search) {
                    viewModel.search(query)
                }
            }

            if (history.isNotEmpty() && results.isEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { h ->
                        NuvioChip(h.query) {
                            query = h.query
                            viewModel.search(h.query)
                        }
                    }
                }
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                    items(results) { row ->
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = row.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                itemsIndexed(row.items) { idx, item ->
                                    NuvioPosterCard(
                                        item = item,
                                        preferEnglish = settings.preferEnglishTitles,
                                        isFirstInRow = idx == 0,
                                        onRequestOpenDrawer = { drawerOpen = true },
                                        onClick = { onOpenDetails(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        NuvioSideDrawer(
            visible = drawerOpen,
            currentRoute = "search",
            drawerFocusRequester = drawerFocus,
            onSelectRoute = onNavigate,
            onDismiss = { drawerOpen = false }
        )
    }
}

@Composable
fun NuvioLibraryScreen(
    viewModel: FilmrViewModel,
    onNavigate: (String) -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val library by viewModel.library.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerFocus = remember { FocusRequester() }

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            try { drawerFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                NuvioCircleButton(Icons.Default.Menu, "القائمة") { drawerOpen = true }
                Spacer(modifier = Modifier.width(12.dp))
                Text("مكتبتي (${library.size})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            if (library.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("المكتبة فارغة حالياً", color = Color.LightGray)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    itemsIndexed(library) { idx, lib ->
                        val item = MediaItem(
                            id = lib.mediaId,
                            imdbId = lib.imdbId,
                            type = lib.type,
                            titleAr = lib.title,
                            titleEn = lib.title,
                            posterUrl = lib.posterUrl,
                            backdropUrl = lib.backdropUrl,
                            year = lib.year,
                            rating = lib.rating
                        )
                        NuvioPosterCard(
                            item = item,
                            preferEnglish = settings.preferEnglishTitles,
                            isFirstInRow = idx == 0,
                            onRequestOpenDrawer = { drawerOpen = true },
                            onClick = { onOpenDetails(it) }
                        )
                    }
                }
            }
        }

        NuvioSideDrawer(
            visible = drawerOpen,
            currentRoute = "library",
            drawerFocusRequester = drawerFocus,
            onSelectRoute = onNavigate,
            onDismiss = { drawerOpen = false }
        )
    }
}

@Composable
fun NuvioSettingsScreen(
    viewModel: FilmrViewModel,
    onNavigate: (String) -> Unit
) {
    val addons by viewModel.addons.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    var addonUrlInput by remember { mutableStateOf("") }
    var mdbKeyInput by remember { mutableStateOf(settings.mdbListApiKey) }
    var animeSkipInput by remember { mutableStateOf(settings.animeSkipClientId) }
    var selectedSection by remember { mutableStateOf("addons") }
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerFocus = remember { FocusRequester() }

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            try { drawerFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Right Navigation Pane (Nuvio Settings Style)
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NuvioCircleButton(Icons.Default.Menu, "القائمة") { drawerOpen = true }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("الإعدادات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))

                val sections = listOf(
                    Triple("addons", "الإضافات والمستودعات", Icons.Default.Extension),
                    Triple("tmdb", "إثراء بيانات TMDB", Icons.Default.MovieFilter),
                    Triple("mdblist", "تقييمات MDBList", Icons.Default.Star),
                    Triple("animeskip", "تخطي الأنمي Anime-Skip", Icons.Default.FastForward),
                    Triple("appearance", "المظهر والترجمة", Icons.Default.Palette)
                )
                sections.forEach { (key, title, icon) ->
                    NuvioChip(
                        label = title,
                        selected = selectedSection == key,
                        icon = icon,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedSection = key
                    }
                }
            }

            // Left Content Pane
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedSection) {
                    "addons" -> {
                        item {
                            Text("إضافات Stremio ومستودعات CloudStream", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = addonUrlInput,
                                    onValueChange = { addonUrlInput = it },
                                    placeholder = { Text("الصق رابط manifest.json أو مستودع CloudStream...", color = Color.Gray) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                NuvioChip("تثبيت", selected = true, icon = Icons.Default.Add) {
                                    if (addonUrlInput.isNotBlank()) {
                                        viewModel.installAddon(addonUrlInput)
                                        addonUrlInput = ""
                                    }
                                }
                            }
                            status?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(it, color = NuvioGold, fontSize = 13.sp)
                            }
                        }
                        items(addons) { addon ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NuvioCardBg)
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(addon.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${addon.type.uppercase()} • ${addon.description}", color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                NuvioChip(if (addon.isEnabled) "مفعّل" else "معطّل", selected = addon.isEnabled) {
                                    viewModel.toggleAddon(addon.id, !addon.isEnabled)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                NuvioCircleButton(Icons.Default.Delete, "حذف") {
                                    viewModel.removeAddon(addon.id)
                                }
                            }
                        }
                    }

                    "tmdb" -> {
                        item {
                            Text("إثراء بيانات TMDB", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsToggleRow("تفعيل تحسين TMDB", settings.tmdbEnabled) {
                                viewModel.updateSettings(settings.copy(tmdbEnabled = it))
                            }
                            SettingsToggleRow("تفعيل في الواجهة الرئيسية", settings.tmdbApplyHome) {
                                viewModel.updateSettings(settings.copy(tmdbApplyHome = it))
                            }
                            SettingsToggleRow("تحسين متابعة المشاهدة", settings.tmdbApplyContinue) {
                                viewModel.updateSettings(settings.copy(tmdbApplyContinue = it))
                            }
                            SettingsToggleRow("الصور الفنية والشعارات", settings.tmdbArtwork) {
                                viewModel.updateSettings(settings.copy(tmdbArtwork = it))
                            }
                            SettingsToggleRow("معلومات أساسية (العنوان والقصة)", settings.tmdbBasicInfo) {
                                viewModel.updateSettings(settings.copy(tmdbBasicInfo = it))
                            }
                            SettingsToggleRow("طاقم العمل والمخرجون", settings.tmdbCast) {
                                viewModel.updateSettings(settings.copy(tmdbCast = it))
                            }
                            SettingsToggleRow("جهات الإنتاج والشبكات", settings.tmdbCompanies) {
                                viewModel.updateSettings(settings.copy(tmdbCompanies = it, tmdbNetworks = it))
                            }
                            SettingsToggleRow("الحلقات والمواسم", settings.tmdbEpisodes) {
                                viewModel.updateSettings(settings.copy(tmdbEpisodes = it))
                            }
                            SettingsToggleRow("مقترحات مشابهة", settings.tmdbRecommendations) {
                                viewModel.updateSettings(settings.copy(tmdbRecommendations = it))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لغة TMDB", color = Color.LightGray, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ar-SA" to "العربية", "en-US" to "English", "ja-JP" to "اليابانية").forEach { (code, label) ->
                                    NuvioChip(label, selected = settings.tmdbLanguage == code) {
                                        viewModel.updateSettings(settings.copy(tmdbLanguage = code))
                                    }
                                }
                            }
                        }
                    }

                    "mdblist" -> {
                        item {
                            Text("تقييمات MDBList", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsToggleRow("تفعيل تقييمات MDBList", settings.mdbListEnabled) {
                                viewModel.updateSettings(settings.copy(mdbListEnabled = it))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = mdbKeyInput,
                                    onValueChange = { mdbKeyInput = it },
                                    placeholder = { Text("مفتاح MDBList API Key...", color = Color.Gray) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NuvioChip("حفظ", selected = true) {
                                    viewModel.updateSettings(settings.copy(mdbListApiKey = mdbKeyInput.trim()))
                                }
                            }
                            SettingsToggleRow("IMDb", settings.showImdbRating) { viewModel.updateSettings(settings.copy(showImdbRating = it)) }
                            SettingsToggleRow("Rotten Tomatoes", settings.showRtRating) { viewModel.updateSettings(settings.copy(showRtRating = it)) }
                            SettingsToggleRow("Audience Score", settings.showAudienceRating) { viewModel.updateSettings(settings.copy(showAudienceRating = it)) }
                            SettingsToggleRow("Metacritic", settings.showMcRating) { viewModel.updateSettings(settings.copy(showMcRating = it)) }
                            SettingsToggleRow("MyAnimeList", settings.showMalRating) { viewModel.updateSettings(settings.copy(showMalRating = it)) }
                        }
                    }

                    "animeskip" -> {
                        item {
                            Text("تخطي شارة البداية للأنمي (Anime-Skip)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsToggleRow("تفعيل Anime-Skip", settings.animeSkipEnabled) {
                                viewModel.updateSettings(settings.copy(animeSkipEnabled = it))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = animeSkipInput,
                                    onValueChange = { animeSkipInput = it },
                                    placeholder = { Text("معرف العميل Client ID من anime-skip.com...", color = Color.Gray) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NuvioChip("حفظ", selected = true) {
                                    viewModel.updateSettings(settings.copy(animeSkipClientId = animeSkipInput.trim()))
                                }
                            }
                        }
                    }

                    "appearance" -> {
                        item {
                            Text("المظهر والترجمة الذكية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsToggleRow("الترجمة التلقائية للعربية للنصوص غير المعرّبة", settings.autoTranslateArabic) {
                                viewModel.updateSettings(settings.copy(autoTranslateArabic = it))
                            }
                            SettingsToggleRow("وضع AMOLED الأسود بالكامل", settings.amoledDarkMode) {
                                viewModel.updateSettings(settings.copy(amoledDarkMode = it))
                            }
                            SettingsToggleRow("تفضيل العناوين الإنجليزية", settings.preferEnglishTitles) {
                                viewModel.updateSettings(settings.copy(preferEnglishTitles = it))
                            }
                            SettingsToggleRow("عرض صف متابعة المشاهدة", settings.showContinueWatching) {
                                viewModel.updateSettings(settings.copy(showContinueWatching = it))
                            }
                        }
                    }
                }
            }
        }

        NuvioSideDrawer(
            visible = drawerOpen,
            currentRoute = "settings",
            drawerFocusRequester = drawerFocus,
            onSelectRoute = onNavigate,
            onDismiss = { drawerOpen = false }
        )
    }
}

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NuvioCardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        NuvioChip(if (checked) "مفعّل" else "معطّل", selected = checked) {
            onToggle(!checked)
        }
    }
}

@Composable
fun NuvioPersonScreen(
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val person by viewModel.personDetails.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(NuvioBg).padding(28.dp)) {
        NuvioChip("رجوع", icon = Icons.AutoMirrored.Filled.ArrowBack) { onBack() }
        Spacer(modifier = Modifier.height(16.dp))
        val p = person
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.profileUrl.isNotBlank()) {
                    AsyncImage(
                        model = p.profileUrl,
                        contentDescription = p.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    if (p.birthday.isNotBlank() || p.placeOfBirth.isNotBlank()) {
                        Text("${p.birthday} • ${p.placeOfBirth}", color = Color.LightGray, fontSize = 12.sp)
                    }
                    if (p.biography.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(p.biography, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text("قائمة الأعمال (${p.knownFor.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(p.knownFor) { item ->
                    NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                }
            }
        }
    }
}

@Composable
fun NuvioCompanyScreen(
    viewModel: FilmrViewModel,
    company: CompanyItem,
    onBack: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val topItems by viewModel.companyTopItems.collectAsState()
    val recentItems by viewModel.companyRecentItems.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NuvioBg),
        contentPadding = PaddingValues(28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvioChip("رجوع", icon = Icons.AutoMirrored.Filled.ArrowBack) { onBack() }
                Spacer(modifier = Modifier.width(14.dp))
                Text(company.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (topItems.isEmpty() && recentItems.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        } else {
            if (topItems.isNotEmpty()) {
                item {
                    Text("الأعلى تقييماً", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(topItems) { item ->
                            NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
            if (recentItems.isNotEmpty()) {
                item {
                    Text("مضاف حديثاً", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentItems) { item ->
                            NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                        }
                    }
                }
            }
        }
    }
}
