package com.filmr.tv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
fun NuvioSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "FILMR",
                color = NuvioGold,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            CircularProgressIndicator(
                color = NuvioGold,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun NuvioHomeScreen(
    vm: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onResumeContinue: (WatchProgressEntity) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val rows by vm.catalogRows.collectAsState()
    val progress by vm.watchProgress.collectAsState()
    val isLoading by vm.isInitialLoading.collectAsState()
    val hero by vm.focusedHeroItem.collectAsState()
    val bg = if (settings.amoledBlack) Color.Black else NuvioBg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Menu) {
                    onOpenDrawer()
                    true
                } else false
            }
    ) {
        hero?.let { h ->
            AsyncImage(
                model = h.displayBackdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.65f,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(Brush.horizontalGradient(listOf(Color(0xEE0A0D12), Color(0x440A0D12), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(Brush.verticalGradient(listOf(Color(0x220A0D12), bg)))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NuvioChip(label = "☰ القائمة", selected = false, onClick = onOpenDrawer)
                Text(
                    text = "FILMR",
                    color = NuvioGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                hero?.let { h ->
                    Column(
                        modifier = Modifier.fillMaxWidth(0.65f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (h.displayLogo.isNotBlank()) {
                            AsyncImage(
                                model = h.displayLogo,
                                contentDescription = h.displayTitle,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.height(60.dp).fillMaxWidth(0.55f)
                            )
                        } else {
                            Text(
                                text = h.displayTitle,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (h.year.isNotBlank()) {
                                Text(h.year, color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            if (h.rating > 0) {
                                Text("★ ${String.format("%.1f", h.rating)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            if (h.runtimeMinutes > 0) {
                                Text("${h.runtimeMinutes} د", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                            }
                            if (h.genres.isNotEmpty()) {
                                Text(h.genres.take(3).joinToString(" • "), color = Color(0xFFB0B8C8), fontSize = 13.sp)
                            }
                        }
                        Text(
                            text = h.overviewAr.ifBlank { h.overviewEn },
                            color = Color(0xFFD6DCE8),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isLoading && rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NuvioGold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (settings.showContinueWatching && progress.isNotEmpty()) {
                        item(key = "continue_watching") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "متابعة المشاهدة",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(progress, key = { it.key }) { p ->
                                        ContinueWatchingCard(progress = p, onClick = { onResumeContinue(p) })
                                    }
                                }
                            }
                        }
                    }

                    items(rows, key = { "${it.addonId}_${it.catalogId}_${it.title}" }) { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = row.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(row.items, key = { "${row.catalogId}_${it.id}" }) { item ->
                                    NuvioPosterCard(
                                        item = item,
                                        onFocused = { vm.setFocusedHeroItem(item) },
                                        onClick = { onOpenDetails(item) }
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

@Composable
fun NuvioDetailsScreen(
    vm: FilmrViewModel,
    initialItem: MediaItem,
    onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenCompany: (Int, String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val libraryList by vm.libraryEntities.collectAsState()
    var item by remember { mutableStateOf(initialItem) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var activeTab by remember { mutableStateOf(if (initialItem.type == "series") "episodes" else "cast") }

    var showStreamsModal by remember { mutableStateOf(false) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(false) }
    var targetEpisode by remember { mutableIntStateOf(1) }
    var targetEpTitle by remember { mutableStateOf("") }
    var selectedAddonTab by remember { mutableStateOf("الكل") }

    val inLibrary = libraryList.any { it.id == item.id }

    LaunchedEffect(initialItem.id) {
        item = vm.enrichDetails(initialItem)
        if (item.type == "series") {
            activeTab = "episodes"
            episodes = vm.getSeasonEpisodes(item.tmdbId, 1)
        }
    }

    LaunchedEffect(selectedSeason, item.tmdbId) {
        if (item.type == "series" && item.tmdbId > 0) {
            episodes = vm.getSeasonEpisodes(item.tmdbId, selectedSeason)
        }
    }

    fun openStreamPicker(season: Int, ep: Int, epTitle: String) {
        targetEpisode = ep
        targetEpTitle = epTitle
        selectedAddonTab = "الكل"
        showStreamsModal = true
        loadingStreams = true
    }

    LaunchedEffect(showStreamsModal, selectedSeason, targetEpisode) {
        if (showStreamsModal) {
            streams = vm.fetchUnifiedStreams(item, selectedSeason, targetEpisode)
            loadingStreams = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (settings.amoledBlack) Color.Black else NuvioBg)
    ) {
        AsyncImage(
            model = item.displayBackdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.45f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x660A0D12), Color(0xF50A0D12))))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = item.displayPoster,
                        contentDescription = item.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(175.dp)
                            .height(255.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (item.displayLogo.isNotBlank()) {
                            AsyncImage(
                                model = item.displayLogo,
                                contentDescription = item.displayTitle,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.height(64.dp).fillMaxWidth(0.5f)
                            )
                        } else {
                            Text(
                                text = item.displayTitle,
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.year.isNotBlank()) {
                                Text(item.year, color = Color(0xFFB0B8C8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            if (item.imdbRating.isNotBlank() && settings.mdblistImdb) {
                                RatingBadge("IMDb", item.imdbRating, Color(0xFFF5C518))
                            }
                            if (item.tmdbRating.isNotBlank()) {
                                RatingBadge("TMDB", item.tmdbRating, Color(0xFF01B4E4))
                            }
                            if (item.rtRating.isNotBlank() && settings.mdblistRt) {
                                RatingBadge("RT", item.rtRating, Color(0xFFFA320A))
                            }
                            if (item.metacriticRating.isNotBlank() && settings.mdblistMetacritic) {
                                RatingBadge("MC", item.metacriticRating, Color(0xFF66CC33))
                            }
                            if (item.letterboxdRating.isNotBlank() && settings.mdblistLetterboxd) {
                                RatingBadge("LB", item.letterboxdRating, Color(0xFF00E054))
                            }
                            if (item.malRating.isNotBlank() && settings.mdblistMal) {
                                RatingBadge("MAL", item.malRating, Color(0xFF2E51A2))
                            }
                        }

                        if (item.genres.isNotEmpty()) {
                            Text(
                                text = item.genres.joinToString(" • "),
                                color = NuvioGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = item.overviewAr.ifBlank { item.overviewEn },
                            color = Color(0xFFE0E6F0),
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NuvioChip(
                                label = if (item.type == "series") "▶ تشغيل الحلقة 1" else "▶ مشاهدة الآن",
                                selected = true,
                                onClick = {
                                    openStreamPicker(1, 1, episodes.firstOrNull()?.title ?: "")
                                }
                            )
                            NuvioChip(
                                label = if (inLibrary) "✓ في المكتبة" else "+ أضف للمكتبة",
                                selected = false,
                                onClick = { vm.toggleLibrary(item) }
                            )
                            NuvioChip(
                                label = "✓ تمت المشاهدة",
                                selected = false,
                                onClick = { vm.markAsWatched(item) }
                            )
                            NuvioChip(
                                label = "رجوع",
                                selected = false,
                                onClick = onBack
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.type == "series") {
                        NuvioChip(
                            label = "المواسم والحلقات",
                            selected = activeTab == "episodes",
                            onClick = { activeTab = "episodes" }
                        )
                    }
                    NuvioChip(
                        label = "طاقم العمل والمؤلف",
                        selected = activeTab == "cast",
                        onClick = { activeTab = "cast" }
                    )
                    NuvioChip(
                        label = "جهات الإنتاج والشبكات",
                        selected = activeTab == "companies",
                        onClick = { activeTab = "companies" }
                    )
                    if (item.recommendations.isNotEmpty()) {
                        NuvioChip(
                            label = "أعمال مشابهة",
                            selected = activeTab == "recs",
                            onClick = { activeTab = "recs" }
                        )
                    }
                }
            }

            if (activeTab == "episodes" && item.type == "series") {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items((1..item.seasonsCount).toList()) { sNum ->
                            NuvioChip(
                                label = "الموسم $sNum",
                                selected = selectedSeason == sNum,
                                onClick = { selectedSeason = sNum }
                            )
                        }
                    }
                }
                items(episodes, key = { "${it.season}_${it.episode}" }) { ep ->
                    var epFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { epFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (epFocused) Color(0xFF232B3B) else NuvioCardBg)
                            .border(
                                width = if (epFocused) 2.dp else 1.dp,
                                color = if (epFocused) Color.White else Color(0x1FFFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { openStreamPicker(ep.season, ep.episode, ep.title) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ep.stillUrl.ifBlank { item.displayBackdrop },
                            contentDescription = ep.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(150.dp)
                                .height(86.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${ep.episode}. ${ep.title}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (ep.rating > 0) {
                                    Text("★ ${String.format("%.1f", ep.rating)}", color = NuvioGold, fontSize = 12.sp)
                                }
                                Text("${ep.runtime} د", color = Color(0xFF9AA4B8), fontSize = 12.sp)
                            }
                            Text(
                                text = ep.overview,
                                color = Color(0xFFB8C0D0),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (activeTab == "cast") {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(item.cast, key = { it.id }) { member ->
                            var cFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .width(115.dp)
                                    .onFocusChanged { cFocused = it.isFocused }
                                    .clickable { onOpenPerson(member.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AsyncImage(
                                    model = member.profileUrl ?: "",
                                    contentDescription = member.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(86.dp)
                                        .clip(CircleShape)
                                        .background(NuvioCardBg)
                                        .border(
                                            width = if (cFocused) 2.5.dp else 1.dp,
                                            color = if (cFocused) Color.White else Color(0x33FFFFFF),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = member.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.character,
                                    color = Color(0xFF9AA4B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (activeTab == "companies") {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (item.network.isNotBlank() && item.networkId > 0) {
                            NuvioChip(
                                label = "شبكة: ${item.network}",
                                selected = true,
                                onClick = { onOpenCompany(item.networkId, item.network, true) }
                            )
                        }
                        item.companies.forEach { comp ->
                            NuvioChip(
                                label = comp.name,
                                selected = false,
                                onClick = { onOpenCompany(comp.id, comp.name, false) }
                            )
                        }
                    }
                }
            }

            if (activeTab == "recs") {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(item.recommendations, key = { it.id }) { rec ->
                            NuvioPosterCard(item = rec, onClick = { item = rec })
                        }
                    }
                }
            }
        }

        if (showStreamsModal) {
            val addonTabs = remember(streams) {
                listOf("الكل") + streams.map { it.addonName }.filter { it.isNotBlank() }.distinct()
            }
            val filteredStreams = remember(streams, selectedAddonTab) {
                if (selectedAddonTab == "الكل") streams else streams.filter { it.addonName == selectedAddonTab }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD05070A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .fillMaxHeight(0.82f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF101520))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختر سيرفر المشاهدة • ${item.displayTitle}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        NuvioChip(label = "إغلاق", selected = false, onClick = { showStreamsModal = false })
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(addonTabs) { tabName ->
                            NuvioChip(
                                label = tabName,
                                selected = selectedAddonTab == tabName,
                                onClick = { selectedAddonTab = tabName }
                            )
                        }
                    }

                    if (loadingStreams) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(color = NuvioGold)
                                Text("جاري جلب الروابط من جميع الإضافات...", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    } else if (filteredStreams.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد روابط متاحة في هذا التبويب حالياً", color = Color(0xFF9AA4B8), fontSize = 15.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filteredStreams, key = { it.id }) { st ->
                                var sFocused by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { sFocused = it.isFocused }
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sFocused) Color(0xFF232C3E) else NuvioCardBg)
                                        .border(
                                            width = if (sFocused) 2.dp else 1.dp,
                                            color = if (sFocused) Color.White else Color(0x1FFFFFFF),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            showStreamsModal = false
                                            if (st.url.contains("google.com/search")) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(st.url)))
                                            } else {
                                                onStartStream(item, st, selectedSeason, targetEpisode, targetEpTitle)
                                            }
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (st.quality == "4K") NuvioGold else Color(0xFF263044))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = st.quality,
                                            color = if (st.quality == "4K") Color.Black else Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(st.addonName, color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (st.isTorrent) {
                                                Text("• تورنت مدمج", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (st.sizeText.isNotBlank()) {
                                                Text("• ${st.sizeText}", color = Color(0xFF9AA4B8), fontSize = 12.sp)
                                            }
                                            if (st.seeders > 0) {
                                                Text("• 👤 ${st.seeders}", color = Color(0xFF9AA4B8), fontSize = 12.sp)
                                            }
                                        }
                                        Text(
                                            text = st.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
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
fun RatingBadge(label: String, score: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF171D29))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        Text(score, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun NuvioPersonScreen(
    vm: FilmrViewModel,
    personId: Int,
    onOpenDetails: (MediaItem) -> Unit,
    onBack: () -> Unit
) {
    var details by remember { mutableStateOf<PersonDetails?>(null) }
    LaunchedEffect(personId) { details = vm.getPersonDetails(personId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioChip(label = "رجوع", onClick = onBack)
            Text(details?.name ?: "جاري التحميل...", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        details?.let { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AsyncImage(
                    model = p.profileUrl ?: "",
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(NuvioCardBg)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.birthday.isNotBlank()) {
                        Text("تاريخ الميلاد: ${p.birthday} • ${p.placeOfBirth}", color = NuvioGold, fontSize = 13.sp)
                    }
                    Text(
                        text = p.biography.ifBlank { "لا تتوفر سيرة ذاتية حالياً." },
                        color = Color(0xFFD0D6E0),
                        fontSize = 13.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text("أشهر الأعمال", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(136.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(p.knownFor, key = { it.id }) { work ->
                    NuvioPosterCard(item = work, onClick = { onOpenDetails(work) })
                }
            }
        }
    }
}

@Composable
fun NuvioCompanyScreen(
    vm: FilmrViewModel,
    companyId: Int,
    companyName: String,
    isNetwork: Boolean,
    onOpenDetails: (MediaItem) -> Unit,
    onBack: () -> Unit
) {
    var sortBy by remember { mutableStateOf("popularity.desc") }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(companyId, sortBy) {
        items = vm.discoverByCompanyOrNetwork(companyId, isNetwork, sortBy)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioChip(label = "رجوع", onClick = onBack)
            Text(companyName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioChip(label = "الرائج", selected = sortBy == "popularity.desc", onClick = { sortBy = "popularity.desc" })
            NuvioChip(label = "الأعلى تقييماً", selected = sortBy == "vote_average.desc", onClick = { sortBy = "vote_average.desc" })
            NuvioChip(label = "المضاف حديثاً", selected = sortBy == "primary_release_date.desc", onClick = { sortBy = "primary_release_date.desc" })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(136.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { work ->
                NuvioPosterCard(item = work, onClick = { onOpenDetails(work) })
            }
        }
    }
}

@Composable
fun NuvioSearchScreen(
    vm: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenDrawer: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<AddonCatalogRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            searching = true
            rows = vm.searchAll(query)
            searching = false
        } else {
            rows = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioChip(label = "☰ القائمة", onClick = onOpenDrawer)
            NuvioWhiteTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "ابحث عن فيلم، مسلسل، أو أنمي...",
                modifier = Modifier.weight(1f)
            )
        }
        if (searching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NuvioGold)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(rows, key = { "${it.addonId}_${it.title}" }) { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(r.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(r.items, key = { it.id }) { m ->
                                NuvioPosterCard(item = m, onClick = { onOpenDetails(m) })
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
    vm: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val library by vm.library.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioChip(label = "☰ القائمة", onClick = onOpenDrawer)
            Text("مكتبتي (${library.size})", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (library.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("مكتبتك فارغة حالياً — أضف أعمالك المفضلة من صفحة التفاصيل", color = Color(0xFF9AA4B8))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(136.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(library, key = { it.id }) { item ->
                    NuvioPosterCard(item = item, onClick = { onOpenDetails(item) })
                }
            }
        }
    }
}

@Composable
fun NuvioSettingsScreen(
    vm: FilmrViewModel,
    onOpenDrawer: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val addons by vm.addons.collectAsState()
    val statusMsg by vm.addonStatusMsg.collectAsState()
    var section by remember { mutableStateOf("services") }
    var urlInput by remember { mutableStateOf("") }
    var mdblistInput by remember { mutableStateOf(settings.mdblistApiKey) }
    var animeSkipInput by remember { mutableStateOf(settings.animeSkipClientId) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioBg)
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(NuvioCardBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NuvioChip(label = "☰ القائمة الرئيسية", onClick = onOpenDrawer)
            Spacer(Modifier.height(6.dp))
            Text("الإعدادات", color = NuvioGold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            NuvioChip(label = "الخدمات المرتبطة", selected = section == "services", onClick = { section = "services" })
            NuvioChip(label = "الإضافات والمستودعات", selected = section == "addons", onClick = { section = "addons" })
            NuvioChip(label = "المظهر والتشغيل", selected = section == "general", onClick = { section = "general" })
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(NuvioCardBg)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (section == "services") {
                item {
                    Text("إثراء بيانات TMDB", color = NuvioGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    SettingsToggleRow("تفعيل تحسين TMDB", settings.tmdbEnabled) { v -> vm.updateSettings { it.copy(tmdbEnabled = v) } }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NuvioChip("العربية (ar-SA)", selected = settings.tmdbLanguage == "ar-SA") {
                            vm.updateSettings { it.copy(tmdbLanguage = "ar-SA") }
                        }
                        NuvioChip("English (en-US)", selected = settings.tmdbLanguage == "en-US") {
                            vm.updateSettings { it.copy(tmdbLanguage = "en-US") }
                        }
                    }
                }
                item { SettingsToggleRow("الصور الفنية والشعارات (Artwork)", settings.tmdbArtwork) { v -> vm.updateSettings { it.copy(tmdbArtwork = v) } } }
                item { SettingsToggleRow("المعلومات الأساسية والوصف", settings.tmdbBasicInfo) { v -> vm.updateSettings { it.copy(tmdbBasicInfo = v) } } }
                item { SettingsToggleRow("طاقم العمل والمؤلف", settings.tmdbCast) { v -> vm.updateSettings { it.copy(tmdbCast = v) } } }
                item { SettingsToggleRow("شركات الإنتاج", settings.tmdbCompanies) { v -> vm.updateSettings { it.copy(tmdbCompanies = v) } } }
                item { SettingsToggleRow("القنوات والشبكات", settings.tmdbNetworks) { v -> vm.updateSettings { it.copy(tmdbNetworks = v) } } }
                item { SettingsToggleRow("الحلقات والمواسم", settings.tmdbEpisodes) { v -> vm.updateSettings { it.copy(tmdbEpisodes = v) } } }
                item { SettingsToggleRow("الإعلانات التشويقية", settings.tmdbTrailers) { v -> vm.updateSettings { it.copy(tmdbTrailers = v) } } }
                item { SettingsToggleRow("الأعمال المقترحة", settings.tmdbRecommendations) { v -> vm.updateSettings { it.copy(tmdbRecommendations = v) } } }
                item { SettingsToggleRow("سلاسل الأفلام (Collections)", settings.tmdbCollections) { v -> vm.updateSettings { it.copy(tmdbCollections = v) } } }

                item {
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Text("تقييمات MDBList المتعددة", color = NuvioGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NuvioWhiteTextField(
                            value = mdblistInput,
                            onValueChange = { mdblistInput = it },
                            placeholder = "أدخل مفتاح MDBList API Key",
                            modifier = Modifier.weight(1f)
                        )
                        NuvioChip("حفظ", selected = true) {
                            vm.updateSettings { it.copy(mdblistApiKey = mdblistInput.trim()) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NuvioChip("IMDb", selected = settings.mdblistImdb) { vm.updateSettings { it.copy(mdblistImdb = !it.mdblistImdb) } }
                        NuvioChip("Rotten Tomatoes", selected = settings.mdblistRt) { vm.updateSettings { it.copy(mdblistRt = !it.mdblistRt) } }
                        NuvioChip("Metacritic", selected = settings.mdblistMetacritic) { vm.updateSettings { it.copy(mdblistMetacritic = !it.mdblistMetacritic) } }
                        NuvioChip("Letterboxd", selected = settings.mdblistLetterboxd) { vm.updateSettings { it.copy(mdblistLetterboxd = !it.mdblistLetterboxd) } }
                        NuvioChip("MAL", selected = settings.mdblistMal) { vm.updateSettings { it.copy(mdblistMal = !it.mdblistMal) } }
                    }
                }

                item {
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Text("تخطي شارات الأنمي (Anime-Skip)", color = NuvioGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NuvioWhiteTextField(
                            value = animeSkipInput,
                            onValueChange = { animeSkipInput = it },
                            placeholder = "أدخل Anime-Skip Client ID",
                            modifier = Modifier.weight(1f)
                        )
                        NuvioChip("حفظ", selected = true) {
                            vm.updateSettings { it.copy(animeSkipClientId = animeSkipInput.trim()) }
                        }
                    }
                }
            }

            if (section == "addons") {
                item {
                    Text("إضافة مصدر Stremio أو مستودع CloudStream", color = NuvioGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NuvioWhiteTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = "ألصق رابط manifest.json أو repo.json هنا...",
                            modifier = Modifier.weight(1f)
                        )
                        NuvioChip("+ Stremio", selected = true) {
                            if (urlInput.isNotBlank()) {
                                vm.addStremioAddon(urlInput)
                                urlInput = ""
                            }
                        }
                        NuvioChip("+ CloudStream", selected = false) {
                            if (urlInput.isNotBlank()) {
                                vm.importCloudStreamRepo(urlInput)
                                urlInput = ""
                            }
                        }
                    }
                }
                if (statusMsg.isNotBlank()) {
                    item { Text(statusMsg, color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                items(addons, key = { it.id }) { ad ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NuvioSurface)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ad.name} (${ad.type})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(ad.description, color = Color(0xFF9AA4B8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NuvioChip(if (ad.isEnabled) "مفعّل" else "معطّل", selected = ad.isEnabled) { vm.toggleAddon(ad) }
                            NuvioChip("حذف", selected = false) { vm.deleteAddon(ad.id) }
                        }
                    }
                }
            }

            if (section == "general") {
                item {
                    Text("المظهر والتشغيل", color = NuvioGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item { SettingsToggleRow("وضع AMOLED الأسود بالكامل", settings.amoledBlack) { v -> vm.updateSettings { it.copy(amoledBlack = v) } } }
                item { SettingsToggleRow("تفضيل العناوين الإنجليزية", settings.preferEnglishTitles) { v -> vm.updateSettings { it.copy(preferEnglishTitles = v) } } }
                item { SettingsToggleRow("إظهار قسم متابعة المشاهدة", settings.showContinueWatching) { v -> vm.updateSettings { it.copy(showContinueWatching = v) } } }
                item { SettingsToggleRow("تشغيل الحلقة التالية تلقائياً", settings.autoPlayNext) { v -> vm.updateSettings { it.copy(autoPlayNext = v) } } }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF232B3B) else NuvioSurface)
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
