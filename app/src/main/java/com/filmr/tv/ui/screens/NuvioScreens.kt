package com.filmr.tv.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NuvioSplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("FILMR", color = NuvioGold, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            CircularProgressIndicator(color = NuvioGold, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
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
    val hero by vm.focusedHeroItem.collectAsState()
    val bg = if (settings.amoledBlack) Color.Black else NuvioBg

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                    onOpenDrawer()
                    false
                } else false
            }
    ) {
        hero?.let { h ->
            AsyncImage(
                model = h.displayBackdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color(0x330A0D12), Color(0xEE0A0D12)))
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x220A0D12), Color(0xF50A0D12)))
                )
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(195.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                hero?.let { h ->
                    Column(Modifier.fillMaxWidth(0.68f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (h.displayLogo.isNotBlank()) {
                            AsyncImage(
                                model = h.displayLogo,
                                contentDescription = h.displayTitle,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier.height(62.dp).fillMaxWidth(0.6f)
                            )
                        } else {
                            Text(h.displayTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (h.year.isNotBlank()) Text(h.year, color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (h.rating > 0) Text("★ ${String.format("%.1f", h.rating)}", color = Color.White, fontSize = 13.sp)
                            if (h.runtimeMinutes > 0) Text("${h.runtimeMinutes} د", color = Color(0xFFB0B8C8), fontSize = 13.sp)
                            if (h.genres.isNotEmpty()) Text(h.genres.take(3).joinToString(" • "), color = Color(0xFFB0B8C8), fontSize = 13.sp)
                        }
                        Text(
                            h.overviewAr.ifBlank { h.overviewEn },
                            color = Color(0xFFD6DCE8),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                if (settings.showContinueWatching && progress.isNotEmpty()) {
                    item {
                        Text("متابعة المشاهدة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(progress) { p ->
                                Column(
                                    Modifier.width(205.dp).clip(RoundedCornerShape(10.dp)).background(NuvioCardBg)
                                        .clickable { onResumeContinue(p) }.padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = p.backdropUrl.ifBlank { p.posterUrl },
                                        contentDescription = p.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(102.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p.type == "series") Text("الموسم ${p.season} • الحلقة ${p.episode}", color = NuvioGold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                items(rows) { row ->
                    Column {
                        Text(row.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(row.items) { item ->
                                NuvioPosterCard(
                                    item = item,
                                    onFocused = { vm.setFocusedHero(item) },
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

@Composable
fun NuvioDetailsScreen(
    initialItem: MediaItem,
    vm: FilmrViewModel,
    onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit
) {
    var item by remember { mutableStateOf(initialItem) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(true) }
    val library by vm.library.collectAsState()
    val inLib = library.any { it.id == item.id }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialItem.id) {
        launch {
            item = vm.loadFullDetails(initialItem)
            if (item.type == "series") {
                episodes = vm.loadEpisodes(item, selectedSeason)
            }
        }
        launch {
            loadingStreams = true
            streams = vm.fetchStreamsParallel(initialItem, selectedSeason, selectedEpisode)
            loadingStreams = false
        }
    }

    Box(Modifier.fillMaxSize().background(NuvioBg)) {
        AsyncImage(model = item.displayBackdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x660A0D12), Color(0xF80A0D12)))))

        LazyColumn(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.displayPoster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(145.dp).height(210.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.displayLogo.isNotBlank()) {
                            AsyncImage(model = item.displayLogo, contentDescription = item.displayTitle, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.height(54.dp).fillMaxWidth(0.5f))
                        } else {
                            Text(item.displayTitle, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("${item.year} • ★ ${String.format("%.1f", item.rating)} • ${item.genres.joinToString("، ")}", color = NuvioGold, fontSize = 13.sp)
                        Text(item.overviewAr.ifBlank { item.overviewEn }, color = Color(0xFFD6DCE8), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (streams.isNotEmpty()) {
                                NuvioChip("▶ تشغيل سريع", selected = true) {
                                    val epName = episodes.find { it.episode == selectedEpisode }?.title ?: item.displayTitle
                                    onStartStream(item, streams.first(), selectedSeason, selectedEpisode, epName)
                                }
                            }
                            NuvioChip(if (inLib) "✓ في المكتبة" else "+ إضافة للمكتبة") { vm.toggleLibrary(item) }
                        }
                    }
                }
            }

            if (item.type == "series") {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..item.seasonsCount).toList()) { s ->
                            NuvioChip("الموسم $s", selected = s == selectedSeason) {
                                selectedSeason = s
                                selectedEpisode = 1
                                scope.launch {
                                    episodes = vm.loadEpisodes(item, s)
                                    loadingStreams = true
                                    streams = vm.fetchStreamsParallel(item, s, 1)
                                    loadingStreams = false
                                }
                            }
                        }
                    }
                }
                if (episodes.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodes) { ep ->
                                NuvioChip("ح${ep.episode}: ${ep.title.take(18)}", selected = ep.episode == selectedEpisode) {
                                    selectedEpisode = ep.episode
                                    scope.launch {
                                        loadingStreams = true
                                        streams = vm.fetchStreamsParallel(item, selectedSeason, ep.episode)
                                        loadingStreams = false
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("مصادر البث المتاحة (${streams.size})", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            if (loadingStreams) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = NuvioGold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("جاري البحث في الإضافات بالتوازي...", color = NuvioGold, fontSize = 14.sp)
                    }
                }
            } else if (streams.isEmpty()) {
                item { Text("لا توجد روابط تشغيل متاحة حالياً لهذا العمل.", color = Color(0xFF9BA3B4)) }
            } else {
                items(streams) { st ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NuvioCardBg)
                            .border(1.dp, Color(0x33E5A93C), RoundedCornerShape(10.dp))
                            .clickable {
                                val epName = episodes.find { it.episode == selectedEpisode }?.title ?: item.displayTitle
                                onStartStream(item, st, selectedSeason, selectedEpisode, epName)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("[${st.quality}] ${st.addonName} • ${st.name}", color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(st.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("▶ تشغيل", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NuvioSearchScreen(vm: FilmrViewModel, onOpenDetails: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<AddonCatalogRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val history by vm.searchHistory.collectAsState()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            rows = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        rows = vm.liveSearch(query)
        searching = false
        vm.recordSearchQuery(query)
    }

    Column(Modifier.fillMaxSize().background(NuvioBg).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioWhiteTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "ابحث فوراً في الأفلام والمسلسلات...",
                modifier = Modifier.weight(1f)
            )
            if (query.isNotBlank()) {
                NuvioChip("✕ مسح") { query = "" }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (query.isBlank() && history.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("سجل البحث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                NuvioChip("مسح الكل") { vm.clearSearchHistory() }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { h ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NuvioChip(h.query) { query = h.query }
                        NuvioChip("×") { vm.deleteSearchQuery(h.query) }
                    }
                }
            }
        } else if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = NuvioGold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("جاري البحث الفوري...", color = NuvioGold)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(rows) { row ->
                    Column {
                        Text(row.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(row.items) { NuvioPosterCard(it) { onOpenDetails(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NuvioLibraryScreen(vm: FilmrViewModel, onOpenDetails: (MediaItem) -> Unit) {
    val entities by vm.libraryEntities.collectAsState()
    val watchedHistory by vm.watchHistory.collectAsState()
    val watchedIds = remember(watchedHistory) { watchedHistory.map { it.mediaId }.toSet() }

    var cloudTab by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf("all") }
    var sortOrder by remember { mutableStateOf("recent") }
    var watchFilter by remember { mutableStateOf("all") }

    val filtered = remember(entities, cloudTab, typeFilter, sortOrder, watchFilter, watchedIds) {
        var list = entities.filter { if (cloudTab) it.isCloud else true }
        if (typeFilter != "all") list = list.filter { it.type == typeFilter }
        if (watchFilter == "watched") list = list.filter { it.id in watchedIds }
        if (watchFilter == "unwatched") list = list.filter { it.id !in watchedIds }
        list = when (sortOrder) {
            "oldest" -> list.sortedBy { it.addedAt }
            "az" -> list.sortedBy { it.titleAr.ifBlank { it.titleEn } }
            "za" -> list.sortedByDescending { it.titleAr.ifBlank { it.titleEn } }
            else -> list.sortedByDescending { it.addedAt }
        }
        list.map {
            MediaItem(it.id, it.imdbId, it.tmdbId, it.type, it.titleAr, it.titleEn, it.posterUrl, it.backdropUrl, "", it.overviewAr, "", it.rating, it.year)
        }
    }

    Column(Modifier.fillMaxSize().background(NuvioBg).padding(28.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("المكتبة (${filtered.size})", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            NuvioChip("محفوظ", selected = !cloudTab) { cloudTab = false }
            NuvioChip("سحابي", selected = cloudTab) { cloudTab = true }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { NuvioChip("الكل", selected = typeFilter == "all") { typeFilter = "all" } }
            item { NuvioChip("أفلام", selected = typeFilter == "movie") { typeFilter = "movie" } }
            item { NuvioChip("مسلسلات", selected = typeFilter == "series") { typeFilter = "series" } }
            item { NuvioChip("أضيف حديثاً", selected = sortOrder == "recent") { sortOrder = "recent" } }
            item { NuvioChip("الاسم أ-ي", selected = sortOrder == "az") { sortOrder = "az" } }
            item { NuvioChip("تمت مشاهدته", selected = watchFilter == "watched") { watchFilter = if (watchFilter == "watched") "all" else "watched" } }
            item { NuvioChip("لم يُشاهد", selected = watchFilter == "unwatched") { watchFilter = if (watchFilter == "unwatched") "all" else "unwatched" } }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered) { NuvioPosterCard(it) { onOpenDetails(it) } }
        }
    }
}

@Composable
fun NuvioAddonsScreen(vm: FilmrViewModel) {
    val addons by vm.addons.collectAsState()
    val session by vm.session.collectAsState()
    val statusMsg by vm.addonStatusMsg.collectAsState()

    var selectedTab by remember { mutableStateOf("stremio") }
    var stremioUrl by remember { mutableStateOf("") }
    var csRepoUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().background(NuvioBg).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مدير الإضافات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                NuvioChip("إضافات Stremio", selected = selectedTab == "stremio") { selectedTab = "stremio" }
                NuvioChip("مستودعات CloudStream", selected = selectedTab == "cloudstream") { selectedTab = "cloudstream" }
            }
        }

        if (statusMsg.isNotBlank()) {
            item { Text(statusMsg, color = NuvioGold, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }

        if (selectedTab == "stremio") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NuvioWhiteTextField(stremioUrl, { stremioUrl = it }, "رابط إضافة Stremio (manifest.json)", Modifier.weight(1f))
                    NuvioChip("+ تثبيت Stremio", selected = true) {
                        if (stremioUrl.isNotBlank()) { vm.addStremioAddon(stremioUrl); stremioUrl = "" }
                    }
                }
            }
            item {
                if (session == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NuvioWhiteTextField(email, { email = it }, "بريد Stremio", Modifier.weight(1f))
                        NuvioWhiteTextField(pass, { pass = it }, "كلمة المرور", Modifier.weight(1f))
                        NuvioChip("مزامنة الحساب") { vm.loginStremio(email, pass) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("متصل بحساب: ${session?.email}", color = NuvioGold, fontWeight = FontWeight.Bold)
                        NuvioChip("تحديث المزامنة") { session?.authKey?.let { vm.syncAllStremioData(it) } }
                        NuvioChip("تسجيل الخروج") { vm.logoutStremio() }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NuvioWhiteTextField(csRepoUrl, { csRepoUrl = it }, "رابط مستودع CloudStream (مثل repo.json أو raw)", Modifier.weight(1f))
                    NuvioChip("+ تثبيت المستودع", selected = true) {
                        if (csRepoUrl.isNotBlank()) { vm.addCloudStreamRepo(csRepoUrl); csRepoUrl = "" }
                    }
                }
            }
        }

        val displayedAddons = addons.filter { it.type == selectedTab }
        items(displayedAddons) { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NuvioCardBg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(a.description.ifBlank { a.url }, color = Color(0xFF9BA3B4), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NuvioChip(if (a.isEnabled) "مفعّلة" else "معطّلة", selected = a.isEnabled) { vm.toggleAddon(a) }
                    NuvioChip("حذف") { vm.deleteAddon(a.id) }
                }
            }
        }
    }
}

@Composable
fun NuvioSettingsScreen(vm: FilmrViewModel) {
    val s by vm.settings.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(NuvioBg).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("الإعدادات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NuvioChip(if (s.preferEnglishTitles) "لغة العناوين: الإنجليزية" else "لغة العناوين: العربية", selected = true) {
                    vm.updateSettings { it.copy(preferEnglishTitles = !it.preferEnglishTitles) }
                }
                NuvioChip(if (s.amoledBlack) "الخلفية: AMOLED أسود" else "الخلفية: داكنة", selected = s.amoledBlack) {
                    vm.updateSettings { it.copy(amoledBlack = !it.amoledBlack) }
                }
                NuvioChip(if (s.torrentEnabled) "روابط التورنت: مفعّلة" else "روابط التورنت: معطّلة", selected = s.torrentEnabled) {
                    vm.updateSettings { it.copy(torrentEnabled = !it.torrentEnabled) }
                }
            }
        }
    }
}
