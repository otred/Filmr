package com.filmr.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.filmr.tv.model.*
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun DetailsAndStreamsScreen(
    initialItem: MediaItem,
    vm: FilmrViewModel,
    onBack: () -> Unit,
    onOpenPerson: (Int) -> Unit,
    onOpenNetwork: (Int, String) -> Unit,
    onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by vm.library.collectAsState()
    var item by remember { mutableStateOf(initialItem) }
    var season by remember { mutableIntStateOf(1) }
    var episode by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var selectedAddonFilter by remember { mutableStateOf("الكل") }
    var loadingStreams by remember { mutableStateOf(true) }
    var showArtworkEditor by remember { mutableStateOf(false) }

    val playFocus = remember { FocusRequester() }
    val inLibrary = library.any { it.id == item.id }
    val addonNames = remember(streams) { listOf("الكل") + streams.map { it.addonName }.distinct() }
    val filteredStreams = remember(streams, selectedAddonFilter) { if (selectedAddonFilter == "الكل") streams else streams.filter { it.addonName == selectedAddonFilter } }

    LaunchedEffect(initialItem.id) {
        try { playFocus.requestFocus() } catch (_: Exception) {}
        val loaded = vm.loadFullDetails(initialItem)
        item = loaded
        if (loaded.type == "series") episodes = vm.loadEpisodes(loaded, season)
        loadingStreams = true
        streams = vm.fetchStreams(loaded, season, episode)
        loadingStreams = false
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    FastTvImage(url = item.displayBackdrop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color(0xFF0A0B0E)))))
                    IconButton(onClick = onBack, modifier = Modifier.padding(14.dp).background(Color(0x77000000), CircleShape)) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(item.displayTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.rating > 0) Text("TMDB ★ ${String.format("%.1f", item.rating)}", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (item.mdbRating.isNotBlank()) Text(item.mdbRating, color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (item.year.isNotBlank()) Text(item.year, color = Color.LightGray, fontSize = 12.sp)
                            if (item.runtimeMinutes > 0) Text("${item.runtimeMinutes} د", color = Color.LightGray, fontSize = 12.sp)
                            if (item.genres.isNotEmpty()) Text(item.genres.take(3).joinToString(" • "), color = Color.LightGray, fontSize = 12.sp)
                        }
                        if (item.directors.isNotEmpty()) Text("إخراج: ${item.directors.joinToString(", ")}", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(item.overviewAr, color = Color(0xFFD0D4DC), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionPill("تشغيل", Icons.Filled.PlayArrow, true, Modifier.focusRequester(playFocus)) { streams.firstOrNull()?.let { onStartStream(item, it, season, episode, item.displayTitle) } }
                        TvActionPill(if (inLibrary) "في المكتبة" else "+ المكتبة", if (inLibrary) Icons.Filled.Check else Icons.Filled.Add) { vm.toggleLibrary(item) }
                        TvActionPill("تمت المشاهدة", Icons.Filled.DoneAll) { vm.markAsWatched(item, season, episode) }
                        TvActionPill("الإعلان", Icons.Filled.Movie) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode("${item.titleEn.ifBlank { item.displayTitle }} official trailer", "UTF-8")}"))) } catch (_: Exception) {}
                        }
                        TvActionPill("البوستر", Icons.Filled.Palette) { showArtworkEditor = true }
                    }
                }
            }

            if (item.type == "series") {
                item {
                    Spacer(Modifier.height(14.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..item.seasonsCount.coerceAtLeast(1)).toList()) { s ->
                            var f by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.clip(CircleShape).background(if (season == s) Color.White else if (f) Color(0xFF282D3D) else Color(0xFF161922)).border(if (f) 2.5.dp else 0.dp, Color.White, CircleShape).onFocusChanged { f = it.isFocused }.clickable { season = s; scope.launch { episodes = vm.loadEpisodes(item, s) } }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text("الموسم $s", color = if (season == s) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(episodes) { ep ->
                            var focused by remember { mutableStateOf(false) }
                            Column(modifier = Modifier.width(185.dp).clip(RoundedCornerShape(10.dp)).border(if (focused || episode == ep.episode) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF222736) else Color(0xFF141720)).onFocusChanged { focused = it.isFocused }.clickable { episode = ep.episode; scope.launch { loadingStreams = true; streams = vm.fetchStreams(item, season, ep.episode); loadingStreams = false } }.padding(8.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().height(95.dp).clip(RoundedCornerShape(8.dp))) { FastTvImage(url = ep.stillUrl.ifBlank { item.displayBackdrop }, modifier = Modifier.fillMaxSize()) }
                                Spacer(Modifier.height(6.dp))
                                Text("الحلقة ${ep.episode}: ${ep.title}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ep.released, color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("مصادر المشاهدة المتاحة (${filteredStreams.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (addonNames.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(addonNames) { name ->
                                var f by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.clip(CircleShape).background(if (selectedAddonFilter == name) Color.White else Color(0xFF181B24)).border(if (f) 2.5.dp else 0.dp, Color.White, CircleShape).onFocusChanged { f = it.isFocused }.clickable { selectedAddonFilter = name }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(name, color = if (selectedAddonFilter == name) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (loadingStreams) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("جاري جلب السيرفرات من Stremio و CloudStream...", color = Color.LightGray, fontSize = 13.sp)
                        }
                    } else if (filteredStreams.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد مصادر متاحة حالياً — تأكد من تفعيل إضافاتك في تبويب الإضافات.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            items(filteredStreams) { st ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF222634), RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF242938) else Color(0xFF141720)).onFocusChanged { focused = it.isFocused }.clickable { onStartStream(item, st, season, episode, item.displayTitle) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(st.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(st.title, color = Color(0xFFB8BDC8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (st.isTorrent) "${st.quality} • Torrent" else st.quality, color = Color.White, fontSize = 11.sp, modifier = Modifier.background(Color(0xFF282E3E), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            if (item.cast.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text("طاقم التمثيل", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(10.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(item.cast) { member ->
                                var focused by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.width(92.dp).onFocusChanged { focused = it.isFocused }.clickable { onOpenPerson(member.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.size(68.dp).clip(CircleShape).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF2C3140), CircleShape).background(Color(0xFF1A1D26))) { FastTvImage(url = member.profileUrl, modifier = Modifier.fillMaxSize()) }
                                    Spacer(Modifier.height(4.dp))
                                    Text(member.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(member.character, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (item.network.isNotBlank() && item.networkId != 0) {
                item {
                    var focused by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text("جهة الإنتاج / شبكة العرض", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF252A38), RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF242938) else Color(0xFF161922)).onFocusChanged { focused = it.isFocused }.clickable { onOpenNetwork(item.networkId, item.network) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.networkLogoUrl.isNotBlank()) { FastTvImage(url = item.networkLogoUrl, contentScale = ContentScale.Fit, modifier = Modifier.height(24.dp)); Spacer(Modifier.width(10.dp)) }
                            Text(item.network, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showArtworkEditor) CustomArtworkDialog(item = item, vm = vm, onDismiss = { showArtworkEditor = false; scope.launch { item = vm.loadFullDetails(item) } })
}

@Composable
private fun TvActionPill(label: String, icon: ImageVector, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clip(CircleShape).background(if (primary || focused) Color.White else Color(0xFF1A1E29)).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF2F3545), CircleShape).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (primary || focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (primary || focused) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PersonProfileScreen(personId: Int, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var person by remember { mutableStateOf<PersonDetails?>(null) }
    LaunchedEffect(personId) { person = vm.loadPersonDetails(personId) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            val p = person
            if (p == null) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Box(modifier = Modifier.size(95.dp).clip(CircleShape).background(Color(0xFF1A1D26))) { FastTvImage(url = p.profileUrl, modifier = Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (p.birthday.isNotBlank()) Text("تاريخ الميلاد: ${p.birthday} • ${p.placeOfBirth}", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(p.biography, color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("أعمال الممثل (${p.credits.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(p.credits) { work ->
                        var focused by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.width(114.dp).onFocusChanged { focused = it.isFocused }.clickable { onOpenDetails(work) }) {
                            Box(modifier = Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))) { FastTvImage(url = work.displayPoster, modifier = Modifier.fillMaxSize()) }
                            Spacer(Modifier.height(4.dp))
                            Text(work.displayTitle, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkCatalogScreen(networkId: Int, networkName: String, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var works by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(networkId) { works = vm.loadNetworkWorks(networkId) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Text("أعمال $networkName", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(114.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(works) { item ->
                    var focused by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.onFocusChanged { focused = it.isFocused }.clickable { onOpenDetails(item) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))) { FastTvImage(url = item.displayPoster, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(4.dp))
                        Text(item.displayTitle, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
