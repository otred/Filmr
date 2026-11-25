package com.filmr.tv.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import com.filmr.tv.viewmodel.FilmrViewModel
import kotlinx.coroutines.launch

@Composable
fun DetailsAndStreamsScreen(initialItem: MediaItem, vm: FilmrViewModel, onBack: () -> Unit, onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val addons by vm.addons.collectAsState()
    val accent = Color(settings.accentHex)

    var item by remember { mutableStateOf(initialItem) }
    var season by remember { mutableIntStateOf(1) }
    var selectedEp by remember { mutableStateOf(EpisodeItem(1, 1, "الحلقة 1", "", "", "")) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(false) }
    var showArtworkDialog by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf(ArtworkGallery()) }

    LaunchedEffect(initialItem.tmdbId, initialItem.imdbId) {
        item = vm.loadFullDetails(initialItem)
        if (item.type == "series" && item.tmdbId != 0) {
            episodes = vm.loadEpisodes(item.tmdbId, season)
            episodes.firstOrNull()?.let { selectedEp = it }
        }
    }

    fun loadAllSources() {
        val idForAddon = item.imdbId.ifBlank { item.id }
        if (idForAddon.isBlank()) return
        scope.launch {
            loadingStreams = true
            val stremioList = StremioEngine.fetchAllStreams(addons, item.type, idForAddon, season, selectedEp.episode)
            val csList = CloudStreamEngine.fetchStreams(addons, item.titleEn.ifBlank { item.titleAr }, season, selectedEp.episode)
            streams = (stremioList + csList).sortedByDescending { it.seeders }
            loadingStreams = false
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF050507)), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                AsyncImage(model = item.displayBackdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66050507), Color(0xFF050507)))))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0x99000000), RoundedCornerShape(10.dp))) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(item.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (item.rating > 0) Text("★ ${"%.1f".format(item.rating)}", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        if (item.mdbRating.isNotBlank()) Text("MDB ${item.mdbRating}", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                        if (item.year.isNotBlank()) Text(item.year, color = Color(0xFFB8BCC6))
                        if (item.network.isNotBlank()) Text(item.network, color = accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                if (item.overviewAr.isNotBlank()) Text(item.overviewAr, color = Color(0xFFD4D7DE), fontSize = 14.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { loadAllSources() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("جلب المصادر")
                    }
                    OutlinedButton(onClick = { vm.toggleLibrary(item) }) { Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("المكتبة", color = Color.White) }
                    OutlinedButton(onClick = {
                        val nextEn = !item.useEnglishTitle
                        item = item.copy(useEnglishTitle = nextEn)
                        vm.saveCustomArtwork(item.id, item.customPosterUrl, item.customBackdropUrl, nextEn)
                    }) { Text(if (item.useEnglishTitle) "الاسم: EN" else "الاسم: AR", color = Color.White) }
                    OutlinedButton(onClick = {
                        scope.launch { gallery = vm.loadArtworkGallery(item.tmdbId, item.type); showArtworkDialog = true }
                    }) { Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("البوسترات", color = Color.White) }
                }
            }
        }
        if (item.type == "series" && item.seasonsCount > 0) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.padding(horizontal = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in 1..item.seasonsCount) {
                        FilterChip(selected = s == season, onClick = {
                            season = s
                            scope.launch { episodes = vm.loadEpisodes(item.tmdbId, s); episodes.firstOrNull()?.let { selectedEp = it } }
                        }, label = { Text("الموسم $s") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(episodes, key = { "${it.season}_${it.episode}" }) { ep ->
                        val active = ep.episode == selectedEp.episode
                        Column(modifier = Modifier.width(170.dp).clip(RoundedCornerShape(8.dp)).background(if (active) Color(0xFF232734) else Color(0xFF14161D)).border(if (active) 1.5.dp else 0.dp, if (active) accent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedEp = ep; loadAllSources() }.padding(8.dp)) {
                            Text("الحلقة ${ep.episode}", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(ep.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("المصادر المتاحة (Stremio + CloudStream)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 18.dp))
            if (loadingStreams) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(18.dp), color = accent)
        }
        items(streams, key = { it.id }) { st ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp).clickable {
                val url = if (st.url.isNotBlank()) st.url else SkipAndTorrentEngine.resolveTorrentUrl(st.infoHash, st.fileIdx, settings.torrentMode)
                if (url.isNotBlank()) onStartStream(item, st.copy(url = url), season, selectedEp.episode, selectedEp.title)
            }, colors = CardDefaults.cardColors(containerColor = Color(0xFF14161D))) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${st.sourceName} • ${st.quality}", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(st.title.ifBlank { st.name }, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (st.size.isNotBlank() || st.seeders > 0) Text("${st.size} ${if (st.seeders > 0) "• بذور: ${st.seeders}" else ""}", color = Color(0xFF9EA3AE), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = "تشغيل", tint = Color.White)
                }
            }
        }
    }

    if (showArtworkDialog) {
        AlertDialog(
            onDismissRequest = { showArtworkDialog = false },
            containerColor = Color(0xFF161820),
            title = { Text("معرض البوسترات والخلفيات الرسمية", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(gallery.posters) { pUrl ->
                        AsyncImage(model = pUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(105.dp).height(155.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            item = item.copy(customPosterUrl = pUrl)
                            vm.saveCustomArtwork(item.id, pUrl, item.customBackdropUrl, item.useEnglishTitle)
                            showArtworkDialog = false
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showArtworkDialog = false }) { Text("إغلاق", color = Color.White) } }
        )
    }
}
