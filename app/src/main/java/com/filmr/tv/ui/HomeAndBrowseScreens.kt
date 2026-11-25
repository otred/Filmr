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
import com.filmr.tv.model.*

@Composable
fun NuvioHomeScreen(
    vm: FilmrViewModel,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayContinue: (WatchProgressEntity) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val progressList by vm.watchProgress.collectAsState()
    val movies by vm.trendingMovies.collectAsState()
    val series by vm.trendingSeries.collectAsState()
    val disney by vm.disneyRow.collectAsState()
    val hbo by vm.hboRow.collectAsState()
    val library by vm.library.collectAsState()
    val accent = Color(settings.accentHex)
    val bg = if (settings.amoledBlack) Color.Black else Color(0xFF0A0B0E)

    Row(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier.width(68.dp).fillMaxHeight().background(Color(0xFF101218)).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("F", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
            IconButton(onClick = {}) { Icon(Icons.Filled.Home, null, tint = accent) }
            IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, null, tint = Color.White) }
            IconButton(onClick = onOpenAddons) { Icon(Icons.Filled.Extension, null, tint = Color.White) }
            IconButton(onClick = onOpenCalendar) { Icon(Icons.Filled.DateRange, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, null, tint = Color.White) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            movies.firstOrNull()?.let { hero ->
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpenDetails(hero) }
                    ) {
                        AsyncImage(model = hero.displayBackdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0A0B0E)))))
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(hero.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(hero.overviewAr, color = Color.LightGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onOpenDetails(hero) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Text("مشاهدة الآن")
                            }
                        }
                    }
                }
            }

            if (settings.showContinueWatching && progressList.isNotEmpty()) {
                item {
                    Text("متابعة المشاهدة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(progressList) { p ->
                            val remMin = ((p.durationMs - p.positionMs).coerceAtLeast(0L) / 60000L)
                            Column(
                                modifier = Modifier.width(220.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF14161D)).clickable { onPlayContinue(p) }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                    AsyncImage(model = p.backdropUrl.ifBlank { p.posterUrl }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Text(
                                        if (p.type == "series") "م${p.season}:ح${p.episode}" else "فيلم",
                                        color = Color.White, fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text("متبقي ${remMin} د", color = Color(0xFFE0E2E6), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                                }
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { vm.removeProgress(p.key) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (movies.isNotEmpty()) item { MediaRow("أفلام رائجة", movies, onOpenDetails) }
            if (series.isNotEmpty()) item { MediaRow("مسلسلات رائجة", series, onOpenDetails) }
            if (settings.disneyRow && disney.isNotEmpty()) item { MediaRow("Disney+", disney, onOpenDetails) }
            if (settings.hboRow && hbo.isNotEmpty()) item { MediaRow("HBO", hbo, onOpenDetails) }
            if (library.isNotEmpty()) item { MediaRow("مكتبتي", library, onOpenDetails) }
        }
    }
}

@Composable
private fun MediaRow(title: String, items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    Column {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item ->
                Column(modifier = Modifier.width(125.dp).clickable { onSelect(item) }) {
                    AsyncImage(
                        model = item.displayPoster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1D26))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(item.displayTitle, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
