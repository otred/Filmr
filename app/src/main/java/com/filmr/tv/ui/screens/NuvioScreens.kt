package com.filmr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

@Composable
fun NuvioTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = "FILMR",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.width(24.dp))
        NuvioChip("الرئيسية", selected = currentRoute == "home", icon = Icons.Default.Home) { onNavigate("home") }
        Spacer(modifier = Modifier.width(10.dp))
        NuvioChip("البحث", selected = currentRoute == "search", icon = Icons.Default.Search) { onNavigate("search") }
        Spacer(modifier = Modifier.width(10.dp))
        NuvioChip("المكتبة", selected = currentRoute == "library", icon = Icons.Default.Bookmark) { onNavigate("library") }
        Spacer(modifier = Modifier.width(10.dp))
        NuvioChip("الإعدادات", selected = currentRoute == "settings", icon = Icons.Default.Settings) { onNavigate("settings") }
        Spacer(modifier = Modifier.weight(1f))
        NuvioChip("تحديث", icon = Icons.Default.Refresh) { onRefresh() }
    }
}

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

    var heroItem by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(rows) {
        if (heroItem == null) {
            heroItem = rows.firstOrNull()?.items?.firstOrNull()
        }
    }

    val bgColor = if (settings.amoledDarkMode) Color.Black else NuvioBg

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        heroItem?.let { hero ->
            AsyncImage(
                model = hero.backdropUrl.ifBlank { hero.posterUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.35f),
                                bgColor.copy(alpha = 0.78f),
                                bgColor
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                NuvioTopBar(
                    currentRoute = "home",
                    onNavigate = onNavigate,
                    onRefresh = { viewModel.refreshCatalogs() }
                )
            }

            item {
                heroItem?.let { hero ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = hero.displayTitle(settings.preferEnglishTitles),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hero.year.isNotBlank()) {
                                Text(hero.year, color = Color.LightGray, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            val score = hero.imdbRating.ifBlank { hero.tmdbRating }
                            if (score.isNotBlank()) {
                                Text("★ $score", color = NuvioGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = if (hero.type == "series") "مسلسل" else "فيلم",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        if (hero.overviewAr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = hero.overviewAr,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        NuvioChip("التفاصيل والمشاهدة", selected = true, icon = Icons.Default.PlayArrow) {
                            onOpenDetails(hero)
                        }
                    }
                }
            }

            if (settings.showContinueWatching && progressList.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "متابعة المشاهدة",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(progressList) { prog ->
                                ContinueWatchingCard(prog) { onResumeProgress(it) }
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
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = row.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(row.items) { item ->
                            NuvioPosterCard(
                                item = item,
                                preferEnglish = settings.preferEnglishTitles,
                                onFocusItem = { heroItem = it },
                                onClick = { onOpenDetails(it) }
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
    viewModel: FilmrViewModel,
    onBack: () -> Unit,
    onPlayStream: (MediaItem, UnifiedStream, Int, Int) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenCompany: (CompanyItem) -> Unit
) {
    val item by viewModel.selectedItem.collectAsState()
    val streams by viewModel.streams.collectAsState()
    val isLoadingStreams by viewModel.isLoadingStreams.collectAsState()
    val library by viewModel.library.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var showStreamsDialog by remember { mutableStateOf(false) }
    var selectedAddonTab by remember { mutableStateOf("الكل") }

    val media = item ?: return
    val inLibrary = library.any { it.mediaId == media.id }
    val bgColor = if (settings.amoledDarkMode) Color.Black else NuvioBg

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        AsyncImage(
            model = media.backdropUrl.ifBlank { media.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(360.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor.copy(alpha = 0.4f), bgColor.copy(alpha = 0.92f), bgColor)
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(28.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NuvioChip("رجوع", icon = Icons.AutoMirrored.Filled.ArrowBack) { onBack() }
                    Spacer(modifier = Modifier.width(12.dp))
                    NuvioChip(
                        label = if (media.type == "series") "تشغيل S${selectedSeason}E${selectedEpisode}" else "تشغيل الآن",
                        selected = true,
                        icon = Icons.Default.PlayArrow
                    ) {
                        selectedAddonTab = "الكل"
                        showStreamsDialog = true
                        viewModel.loadStreamsForItem(media, selectedSeason, selectedEpisode)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    NuvioChip(
                        label = if (inLibrary) "في المكتبة ✓" else "أضف للمكتبة",
                        icon = if (inLibrary) Icons.Default.Check else Icons.Default.Add
                    ) {
                        viewModel.toggleLibrary(media)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = media.displayTitle(settings.preferEnglishTitles),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (media.year.isNotBlank()) Text(media.year, color = Color.LightGray, fontSize = 14.sp)
                    if (settings.showImdbRating && media.imdbRating.isNotBlank()) {
                        Text("IMDb ${media.imdbRating}", color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (settings.showTmdbRating && media.tmdbRating.isNotBlank()) {
                        Text("TMDB ${media.tmdbRating}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (settings.showRtRating && media.rtRating.isNotBlank()) {
                        Text("RT ${media.rtRating}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (settings.showMcRating && media.metacriticRating.isNotBlank()) {
                        Text("MC ${media.metacriticRating}", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                if (media.overviewAr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = media.overviewAr,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            if (media.type == "series") {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("المواسم والحلقات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
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
                                    .width(210.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NuvioCardBg)
                                    .border(
                                        width = if (epFocused) 2.dp else 1.dp,
                                        color = if (epFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { epFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        selectedEpisode = ep.episodeNumber
                                        selectedAddonTab = "الكل"
                                        showStreamsDialog = true
                                        viewModel.loadStreamsForItem(media, selectedSeason, ep.episodeNumber)
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "الحلقة ${ep.episodeNumber}: ${ep.name}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.overview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
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

            if (media.cast.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("طاقم العمل", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(media.cast) { actor ->
                            var focused by remember { mutableStateOf(false) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(96.dp)
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clickable { onOpenPerson(actor.id) }
                            ) {
                                AsyncImage(
                                    model = actor.profileUrl,
                                    contentDescription = actor.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(NuvioCardBg)
                                        .border(
                                            width = if (focused) 2.dp else 1.dp,
                                            color = if (focused) Color.White else Color.White.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = actor.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (media.companies.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("جهات الإنتاج والشبكات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(media.companies) { comp ->
                            NuvioChip(comp.name) { onOpenCompany(comp) }
                        }
                    }
                }
            }
        }

        if (showStreamsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NuvioSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "اختر سيرفر المشاهدة",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        NuvioChip("إغلاق", icon = Icons.Default.Close) { showStreamsDialog = false }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val addonNames = listOf("الكل") + streams.map { it.addonName }.distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(addonNames) { tab ->
                            NuvioChip(tab, selected = selectedAddonTab == tab) {
                                selectedAddonTab = tab
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoadingStreams) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        val filtered = if (selectedAddonTab == "الكل") streams else streams.filter { it.addonName == selectedAddonTab }
                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد روابط متاحة حالياً من الإضافات المفعّلة", color = Color.LightGray)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filtered) { st ->
                                    var sFocused by remember { mutableStateOf(false) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sFocused) Color.White.copy(alpha = 0.16f) else NuvioCardBg)
                                            .border(
                                                width = if (sFocused) 2.dp else 1.dp,
                                                color = if (sFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .onFocusChanged { sFocused = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                showStreamsDialog = false
                                                onPlayStream(media, st, selectedSeason, selectedEpisode)
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(NuvioGold, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(st.quality, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${st.addonName} • ${st.name}",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = st.title,
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (st.isTorrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("تورنت مدمج", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    Column(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        NuvioTopBar(currentRoute = "search", onNavigate = onNavigate, onRefresh = { viewModel.refreshCatalogs() })

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp)
        ) {
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
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
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
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(results) { row ->
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = row.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(row.items) { item ->
                                NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                            }
                        }
                    }
                }
            }
        }
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

    Column(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        NuvioTopBar(currentRoute = "library", onNavigate = onNavigate, onRefresh = { viewModel.refreshCatalogs() })

        Text(
            text = "مكتبتي (${library.size})",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
        )

        if (library.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("المكتبة فارغة حالياً", color = Color.LightGray)
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(library) { lib ->
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
                    NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                }
            }
        }
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

    Column(modifier = Modifier.fillMaxSize().background(NuvioBg)) {
        NuvioTopBar(currentRoute = "settings", onNavigate = onNavigate, onRefresh = { viewModel.refreshCatalogs() })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("الإضافات والمستودعات (Stremio & CloudStream)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                    Spacer(modifier = Modifier.width(12.dp))
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
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${addon.type.uppercase()} • ${addon.description}", color = Color.LightGray, fontSize = 12.sp)
                    }
                    NuvioChip(if (addon.isEnabled) "مفعّلة" else "معطّلة", selected = addon.isEnabled) {
                        viewModel.toggleAddon(addon.id, !addon.isEnabled)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NuvioChip("حذف", icon = Icons.Default.Delete) {
                        viewModel.removeAddon(addon.id)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text("المظهر والبيانات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NuvioChip("وضع AMOLED الأسود", selected = settings.amoledDarkMode) {
                        viewModel.updateSettings(settings.copy(amoledDarkMode = !settings.amoledDarkMode))
                    }
                    NuvioChip("تفضيل العناوين الإنجليزية", selected = settings.preferEnglishTitles) {
                        viewModel.updateSettings(settings.copy(preferEnglishTitles = !settings.preferEnglishTitles))
                    }
                    NuvioChip("تفعيل بيانات TMDB", selected = settings.tmdbEnabled) {
                        viewModel.updateSettings(settings.copy(tmdbEnabled = !settings.tmdbEnabled))
                    }
                }
            }
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
            Text(p.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (p.biography.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(p.biography, color = Color.LightGray, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("أبرز الأعمال", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
    val items by viewModel.companyItems.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(NuvioBg).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NuvioChip("رجوع", icon = Icons.AutoMirrored.Filled.ArrowBack) { onBack() }
            Spacer(modifier = Modifier.width(14.dp))
            Text(company.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(items) { item ->
                    NuvioPosterCard(item, settings.preferEnglishTitles) { onOpenDetails(it) }
                }
            }
        }
    }
}
