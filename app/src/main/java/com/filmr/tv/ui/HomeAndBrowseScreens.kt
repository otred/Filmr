package com.filmr.tv.ui
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.filmr.tv.model.*

@Composable
fun NuvioHomeScreen(vm: FilmrViewModel, onOpenDetails: (MediaItem) -> Unit, onPlayContinue: (WatchProgressEntity) -> Unit, onOpenSearch: () -> Unit, onOpenAddons: () -> Unit, onOpenCalendar: () -> Unit, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val profile by vm.activeProfile.collectAsState()
    val progressList by vm.watchProgress.collectAsState()
    val movies by vm.trendingMovies.collectAsState()
    val series by vm.trendingSeries.collectAsState()
    val netflix by vm.netflixRow.collectAsState()
    val disney by vm.disneyRow.collectAsState()
    val apple by vm.appleRow.collectAsState()
    val hbo by vm.hboRow.collectAsState()
    val accent = Color(settings.accentHex)
    var contextItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(if (settings.amoledBlack) Color(0xFF050507) else Color(0xFF0E1015)), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(profile.colorHex)), contentAlignment = Alignment.Center) {
                        Text(profile.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column {
                        Text("FILMR TV", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                        Text("الملف: ${profile.name}", color = Color(0xFFB0B3B8), fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopBarIconBtn(Icons.Default.Search, "بحث", onOpenSearch)
                    TopBarIconBtn(Icons.Default.DateRange, "التقويم", onOpenCalendar)
                    TopBarIconBtn(Icons.Default.Extension, "الإضافات", onOpenAddons)
                    TopBarIconBtn(Icons.Default.Settings, "الإعدادات", onOpenSettings)
                }
            }
        }
        if (settings.showContinueWatching && progressList.isNotEmpty()) {
            item {
                SectionHeader("متابعة المشاهدة", accent)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(progressList, key = { it.id }) { p ->
                        val ratio = if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        val remMin = ((p.durationMs - p.positionMs).coerceAtLeast(0L) / 60000L)
                        Column(modifier = Modifier.width(230.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF14161D)).clickable { onPlayContinue(p) }) {
                            Box(modifier = Modifier.fillMaxWidth().height(128.dp)) {
                                AsyncImage(model = p.backdropUrl.ifBlank { p.posterUrl }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC050507)))))
                                Text(if (p.type == "series") "م${p.season}:ح${p.episode}" else "فيلم", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                Text("متبقي ${remMin} د", color = Color(0xFFE0E2E6), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp))
                            }
                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(3.dp), color = accent, trackColor = Color(0xFF2B2F3A))
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(p.titleAr.ifBlank { p.titleEn }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.removeProgress(p.id) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color(0xFF9EA3AE), modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
        item { MediaShelf("الأفلام الرائجة", movies, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
        item { MediaShelf("المسلسلات الرائجة", series, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
        item { MediaShelf("Netflix", netflix, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
        item { MediaShelf("Disney+", disney, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
        item { MediaShelf("Apple TV+", apple, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
        item { MediaShelf("HBO / Max", hbo, accent, settings.expandFocusedCard, onOpenDetails) { contextItem = it } }
    }

    contextItem?.let { target ->
        AlertDialog(
            onDismissRequest = { contextItem = null },
            containerColor = Color(0xFF181A22),
            title = { Text(target.displayTitle, color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("اختر إجراءً سريعاً لهذا العمل", color = Color(0xFFB8BCC6)) },
            confirmButton = { Button(onClick = { vm.toggleLibrary(target); contextItem = null }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("إضافة / إزالة من المكتبة") } },
            dismissButton = { TextButton(onClick = { val c = target; contextItem = null; onOpenDetails(c) }) { Text("فتح التفاصيل", color = Color.White) } }
        )
    }
}

@Composable
private fun TopBarIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Color(0xFF181A22), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    Row(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaShelf(title: String, items: List<MediaItem>, accent: Color, expandOnFocus: Boolean, onClick: (MediaItem) -> Unit, onLongPress: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    SectionHeader(title, accent)
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { "${title}_${it.tmdbId}" }) { item ->
            var focused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (focused && expandOnFocus) 1.07f else 1.0f, label = "card_scale")
            Column(modifier = Modifier.width(126.dp).onFocusChanged { focused = it.isFocused }.combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongPress(item) })) {
                Box(modifier = Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(8.dp)).border(if (focused) 2.dp else 0.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(8.dp))) {
                    AsyncImage(model = item.displayPoster, contentDescription = item.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (item.rating > 0) {
                        Text("★ ${"%.1f".format(item.rating)}", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(item.displayTitle, color = if (focused) Color.White else Color(0xFFD0D3DA), fontSize = 12.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
