package com.filmr.tv.ui

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.model.*
import kotlinx.coroutines.launch

@Composable
fun TvPosterCard(item: MediaItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(142.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .border(if (focused) 2.5.dp else 1.dp, if (focused) Color(0xFFE5A93C) else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .background(Color(0xFF141821))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        AsyncImage(
            model = item.displayPoster,
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1D2330))
        )
        Spacer(Modifier.height(6.dp))
        Text(item.displayTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.year.isNotBlank() || item.rating > 0) {
            Text("${item.year} ${if (item.rating > 0) "★ ${String.format("%.1f", item.rating)}" else ""}", color = Color(0xFF9BA3B4), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
fun TvActionChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFE5A93C) else if (focused) Color(0xFF283142) else Color(0xFF171C26))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun NuvioHomeScreen(
    vm: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenDetailsManual: (MediaItem) -> Unit,
    onResumeContinue: (WatchProgressEntity, Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val movies by vm.trendingMovies.collectAsState()
    val series by vm.trendingSeries.collectAsState()
    val disney by vm.disneyRow.collectAsState()
    val hbo by vm.hboRow.collectAsState()
    val addonRows by vm.addonCatalogRows.collectAsState()
    val progress by vm.watchProgress.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val bgColor = if (settings.amoledBlack) Color.Black else Color(0xFF0B0E14)

    Column(Modifier.fillMaxSize().background(bgColor).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FILMR", color = Color(0xFFE5A93C), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                TvActionChip("الرئيسية", selected = true) { vm.refreshHome() }
                TvActionChip("البحث") { onOpenSearch() }
                TvActionChip("المفضلة") { onOpenLibrary() }
                TvActionChip("التقويم") { onOpenCalendar() }
                TvActionChip("الإضافات") { onOpenAddons() }
                TvActionChip("الإعدادات") { onOpenSettings() }
            }
            TvActionChip("👤 ${activeProfile.name}") {
                val next = if (activeProfile.id == "p1") UserProfile("p2", "الأطفال", true) else UserProfile("p1", "الرئيسي")
                vm.selectProfile(next)
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            if (settings.showContinueWatching && progress.isNotEmpty()) {
                item {
                    Text("متابعة المشاهدة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(progress) { p ->
                            Column(
                                Modifier.width(210.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF151A24))
                                    .clickable { onResumeContinue(p, false) }.padding(8.dp)
                            ) {
                                AsyncImage(model = p.backdropUrl.ifBlank { p.posterUrl }, contentDescription = p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(6.dp)))
                                Spacer(Modifier.height(6.dp))
                                Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.type == "series") Text("الموسم ${p.season} • الحلقة ${p.episode}", color = Color(0xFFE5A93C), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (settings.trendingMoviesRow && movies.isNotEmpty()) {
                item {
                    Text("أفلام رائجة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(movies) { TvPosterCard(it) { onOpenDetails(it) } } }
                }
            }

            if (settings.trendingSeriesRow && series.isNotEmpty()) {
                item {
                    Text("مسلسلات رائجة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(series) { TvPosterCard(it) { onOpenDetails(it) } } }
                }
            }

            if (settings.disneyRow && disney.isNotEmpty()) {
                item {
                    Text("أعمال Disney+", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(disney) { TvPosterCard(it) { onOpenDetails(it) } } }
                }
            }

            if (settings.hboRow && hbo.isNotEmpty()) {
                item {
                    Text("أعمال HBO", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(hbo) { TvPosterCard(it) { onOpenDetails(it) } } }
                }
            }

            items(addonRows) { row ->
                Text(row.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(row.items) { TvPosterCard(it) { onOpenDetails(it) } } }
            }
        }
    }
}

@Composable
fun DetailsAndStreamsScreen(
    initialItem: MediaItem,
    vm: FilmrViewModel,
    onBack: () -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenNetwork: (Int, String) -> Unit,
    onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit
) {
    var item by remember { mutableStateOf(initialItem) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(false) }
    val library by vm.library.collectAsState()
    val inLib = library.any { it.id == item.id }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialItem.id) {
        item = vm.loadFullDetails(initialItem)
        if (item.type == "series") {
            episodes = vm.loadEpisodes(item, selectedSeason)
        }
        loadingStreams = true
        streams = vm.fetchStreams(item, selectedSeason, selectedEpisode)
        loadingStreams = false
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0E14))) {
        AsyncImage(model = item.displayBackdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.25f, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x990B0E14), Color(0xFF0B0E14)))))

        LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvActionChip("← رجوع") { onBack() }
                    TvActionChip(if (inLib) "✓ في المفضلة" else "+ أضف للمفضلة") { vm.toggleLibrary(item) }
                    if (item.networkId != 0 && item.network.isNotBlank()) {
                        TvActionChip("الشبكة: ${item.network}") { onOpenNetwork(item.networkId, item.network) }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    AsyncImage(model = item.displayPoster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(150.dp).height(215.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.displayTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${item.year} • ★ ${String.format("%.1f", item.rating)} • ${item.genres.joinToString("، ")}", color = Color(0xFFE5A93C), fontSize = 13.sp)
                        Text(item.overviewAr.ifBlank { item.overviewEn }, color = Color(0xFFD0D5DD), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (item.type == "series") {
                item {
                    Text("المواسم", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..item.seasonsCount).toList()) { s ->
                            TvActionChip("الموسم $s", selected = s == selectedSeason) {
                                selectedSeason = s
                                selectedEpisode = 1
                                scope.launch {
                                    episodes = vm.loadEpisodes(item, s)
                                    loadingStreams = true
                                    streams = vm.fetchStreams(item, s, 1)
                                    loadingStreams = false
                                }
                            }
                        }
                    }
                }

                if (episodes.isNotEmpty()) {
                    item {
                        Text("الحلقات", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodes) { ep ->
                                TvActionChip("ح${ep.episode}: ${ep.title.take(18)}", selected = ep.episode == selectedEpisode) {
                                    selectedEpisode = ep.episode
                                    scope.launch {
                                        loadingStreams = true
                                        streams = vm.fetchStreams(item, selectedSeason, ep.episode)
                                        loadingStreams = false
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("السيرفرات المتاحة (${streams.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TvActionChip("تحديث السيرفرات") {
                        scope.launch {
                            loadingStreams = true
                            streams = vm.fetchStreams(item, selectedSeason, selectedEpisode)
                            loadingStreams = false
                        }
                    }
                }
            }

            if (loadingStreams) {
                item { Text("جاري جلب السيرفرات من إضافات Stremio...", color = Color(0xFFE5A93C)) }
            } else if (streams.isEmpty()) {
                item { Text("لا توجد روابط تشغيل متاحة حالياً. تأكد من تفعيل إضافة Stremio في قسم الإضافات.", color = Color(0xFF9BA3B4)) }
            } else {
                items(streams) { st ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF161B26))
                            .border(1.dp, Color(0x33E5A93C), RoundedCornerShape(10.dp))
                            .clickable {
                                val epName = episodes.find { it.episode == selectedEpisode }?.title ?: item.displayTitle
                                onStartStream(item, st, selectedSeason, selectedEpisode, epName)
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("[${st.quality}] ${st.addonName} - ${st.name}", color = Color(0xFFE5A93C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(st.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("▶ تشغيل", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (item.cast.isNotEmpty()) {
                item {
                    Text("طاقم التمثيل", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(item.cast) { c ->
                            TvActionChip(c.name) { onOpenPerson(c.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionChip("← رجوع") { onBack() }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    scope.launch { results = vm.search(it) }
                },
                placeholder = { Text("ابحث عن فيلم أو مسلسل...", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { TvPosterCard(it) { onOpenDetails(it) } }
        }
    }
}

@Composable
fun LibraryScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val library by vm.library.collectAsState()
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionChip("← رجوع") { onBack() }
            Text("المفضلة (${library.size})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(library) { TvPosterCard(it) { onOpenDetails(it) } }
        }
    }
}

@Composable
fun CalendarScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var entries by remember { mutableStateOf<List<CalendarEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        val cal = java.util.Calendar.getInstance()
        entries = vm.loadCalendarEntries(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionChip("← رجوع") { onBack() }
            Text("جدول الحلقات القادمة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF161B26)).clickable { onOpenDetails(e.item) }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${e.item.displayTitle} • S${e.season}E${e.episode} (${e.title})", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(e.date, color = Color(0xFFE5A93C))
                }
            }
        }
    }
}

@Composable
fun AddonsAndSyncScreen(vm: FilmrViewModel, onBack: () -> Unit) {
    val addons by vm.addons.collectAsState()
    val session by vm.session.collectAsState()
    var addonUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionChip("← رجوع") { onBack() }
                Text("إضافات Stremio ومزامنة الحساب", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = addonUrl, onValueChange = { addonUrl = it }, placeholder = { Text("رابط manifest.json للإضافة", color = Color.Gray) }, singleLine = true, modifier = Modifier.weight(1f))
                TvActionChip("+ تثبيت الإضافة") { if (addonUrl.isNotBlank()) { vm.addStremioAddon(addonUrl); addonUrl = "" } }
            }
        }
        item {
            if (session == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, placeholder = { Text("بريد Stremio", color = Color.Gray) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = password, onValueChange = { password = it }, placeholder = { Text("كلمة المرور", color = Color.Gray) }, singleLine = true, modifier = Modifier.weight(1f))
                    TvActionChip("تسجيل الدخول ومزامنة") { vm.loginStremio(email, password) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("متصل بحساب: ${session?.email}", color = Color(0xFFE5A93C), fontWeight = FontWeight.Bold)
                    TvActionChip("مزامنة الآن") { session?.authKey?.let { vm.syncAllStremioData(it) } }
                    TvActionChip("تسجيل الخروج") { vm.logoutStremio() }
                }
            }
        }
        items(addons) { a ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF161B26)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(a.url, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvActionChip(if (a.isEnabled) "مفعّلة" else "معطّلة", selected = a.isEnabled) { vm.toggleAddon(a) }
                    TvActionChip("حذف") { vm.deleteAddon(a.id) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenAddons: () -> Unit) {
    val s by vm.settings.collectAsState()
    val partyCode by vm.watchPartyCode.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionChip("← رجوع") { onBack() }
                Text("الإعدادات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionChip(if (s.preferEnglishTitles) "العناوين: إنجليزية" else "العناوين: عربية", selected = true) {
                    vm.updateSettings { it.copy(preferEnglishTitles = !it.preferEnglishTitles) }
                }
                TvActionChip(if (s.amoledBlack) "الخلفية: AMOLED أسود" else "الخلفية: داكنة", selected = s.amoledBlack) {
                    vm.updateSettings { it.copy(amoledBlack = !it.amoledBlack) }
                }
                TvActionChip(if (s.torrentEnabled) "التورنت: مفعّل" else "التورنت: معطّل", selected = s.torrentEnabled) {
                    vm.updateSettings { it.copy(torrentEnabled = !it.torrentEnabled) }
                }
                TvActionChip("إدارة الإضافات") { onOpenAddons() }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvActionChip("إنشاء غرفة مشاهدة جماعية") { vm.createWatchPartyCode() }
                if (partyCode.isNotBlank()) Text("رمز الغرفة: $partyCode", color = Color(0xFFE5A93C), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun PersonProfileScreen(personId: Int, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var details by remember { mutableStateOf<PersonDetails?>(null) }
    LaunchedEffect(personId) { details = vm.loadPersonDetails(personId) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp)) {
        TvActionChip("← رجوع") { onBack() }
        Spacer(Modifier.height(12.dp))
        details?.let { p ->
            Text(p.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(p.credits) { TvPosterCard(it) { onOpenDetails(it) } }
            }
        }
    }
}

@Composable
fun NetworkCatalogScreen(networkId: Int, networkName: String, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(networkId) { items = vm.loadNetworkWorks(networkId) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionChip("← رجوع") { onBack() }
            Text(networkName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { TvPosterCard(it) { onOpenDetails(it) } }
        }
    }
}
