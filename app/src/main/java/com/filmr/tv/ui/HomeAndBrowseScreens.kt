package com.filmr.tv.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.filmr.tv.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun FastTvImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val req = remember(url) { ImageRequest.Builder(ctx).data(url).bitmapConfig(Bitmap.Config.RGB_565).crossfade(false).build() }
    AsyncImage(model = req, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
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
    val progressList by vm.watchProgress.collectAsState()
    val movies by vm.trendingMovies.collectAsState()
    val series by vm.trendingSeries.collectAsState()
    val disney by vm.disneyRow.collectAsState()
    val hbo by vm.hboRow.collectAsState()
    val library by vm.library.collectAsState()
    val addonRows by vm.addonCatalogRows.collectAsState()

    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    val heroItem = focusedItem ?: movies.firstOrNull() ?: series.firstOrNull()

    var continueDialogItem by remember { mutableStateOf<WatchProgressEntity?>(null) }
    var cardMenuTarget by remember { mutableStateOf<MediaItem?>(null) }
    var artworkEditorTarget by remember { mutableStateOf<MediaItem?>(null) }
    var sidebarExpanded by remember { mutableStateOf(false) }

    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(movies.isNotEmpty()) { if (movies.isNotEmpty()) try { initialFocusRequester.requestFocus() } catch (_: Exception) {} }

    val bg = if (settings.amoledBlack) Color.Black else Color(0xFF0A0B0E)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier = Modifier.fillMaxSize().background(bg)) {
            Column(
                modifier = Modifier.width(if (sidebarExpanded) 185.dp else 66.dp).fillMaxHeight().background(Color(0xFF0D0F14)).onFocusChanged { sidebarExpanded = it.hasFocus }.padding(vertical = 18.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("F", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (sidebarExpanded) { Spacer(Modifier.width(10.dp)); Text("فلمر", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(4.dp))
                SidebarNavItem(Icons.Filled.Search, "البحث", false, sidebarExpanded, onOpenSearch)
                SidebarNavItem(Icons.Filled.Home, "الرئيسية", true, sidebarExpanded) {}
                SidebarNavItem(Icons.Filled.VideoLibrary, "المكتبة", false, sidebarExpanded, onOpenLibrary)
                SidebarNavItem(Icons.Filled.DateRange, "التقويم", false, sidebarExpanded, onOpenCalendar)
                SidebarNavItem(Icons.Filled.Extension, "الإضافات", false, sidebarExpanded, onOpenAddons)
                Spacer(Modifier.weight(1f))
                SidebarNavItem(Icons.Filled.Settings, "الإعدادات", false, sidebarExpanded, onOpenSettings)
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                heroItem?.let { hero ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(225.dp)) {
                            FastTvImage(url = hero.displayBackdrop, modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0.72f), Color.Transparent))))
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bg))))
                            Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth(0.72f)) {
                                if (hero.displayLogo.isNotBlank()) FastTvImage(url = hero.displayLogo, contentScale = ContentScale.Fit, modifier = Modifier.height(44.dp))
                                else Text(hero.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (hero.type == "series") "مسلسل" else "فيلم", color = Color.LightGray, fontSize = 12.sp)
                                    if (hero.year.isNotBlank()) Text("• ${hero.year}", color = Color.LightGray, fontSize = 12.sp)
                                    if (hero.rating > 0) Text("• ★ ${String.format("%.1f", hero.rating)}", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (hero.mdbRating.isNotBlank()) Text("• ${hero.mdbRating}", color = Color(0xFF81C784), fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(hero.overviewAr, color = Color(0xFFD0D4DC), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (settings.showContinueWatching && progressList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("متابعة المشاهدة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(progressList, key = { it.key }) { p ->
                                    ContinueWatchingCard(progress = p, onClick = { onResumeContinue(p, false) }, onLongClick = { continueDialogItem = p })
                                }
                            }
                        }
                    }
                }

                if (settings.trendingMoviesRow && movies.isNotEmpty()) item { PosterRow("فيلم - Popular", movies, initialFocusRequester, settings.expandFocusedCard, settings.cardExpandDelayMs, { focusedItem = it }, onOpenDetails, { cardMenuTarget = it }) }
                if (settings.trendingSeriesRow && series.isNotEmpty()) item { PosterRow("مسلسل - Popular", series, expandOnFocus = settings.expandFocusedCard, expandDelayMs = settings.cardExpandDelayMs, onFocus = { focusedItem = it }, onClick = onOpenDetails, onLongClick = { cardMenuTarget = it }) }
                items(addonRows) { row -> PosterRow(row.title, row.items, expandOnFocus = settings.expandFocusedCard, expandDelayMs = settings.cardExpandDelayMs, onFocus = { focusedItem = it }, onClick = onOpenDetails, onLongClick = { cardMenuTarget = it }) }
                if (settings.disneyRow && disney.isNotEmpty()) item { PosterRow("Disney+", disney, expandOnFocus = settings.expandFocusedCard, expandDelayMs = settings.cardExpandDelayMs, onFocus = { focusedItem = it }, onClick = onOpenDetails, onLongClick = { cardMenuTarget = it }) }
                if (settings.hboRow && hbo.isNotEmpty()) item { PosterRow("HBO", hbo, expandOnFocus = settings.expandFocusedCard, expandDelayMs = settings.cardExpandDelayMs, onFocus = { focusedItem = it }, onClick = onOpenDetails, onLongClick = { cardMenuTarget = it }) }
                if (library.isNotEmpty()) item { PosterRow("مكتبتي (Stremio)", library, expandOnFocus = settings.expandFocusedCard, expandDelayMs = settings.cardExpandDelayMs, onFocus = { focusedItem = it }, onClick = onOpenDetails, onLongClick = { cardMenuTarget = it }) }
            }
        }
    }

    continueDialogItem?.let { p ->
        val media = MediaItem(p.mediaId, p.imdbId, 0, p.type, p.title, p.title, p.posterUrl, p.backdropUrl)
        AlertDialog(
            onDismissRequest = { continueDialogItem = null },
            containerColor = Color(0xFF161922),
            title = { Text(p.title, color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogOptionBtn("التفاصيل") { continueDialogItem = null; onOpenDetails(media) }
                    DialogOptionBtn("تشغيل يدوي (اختيار الإضافة)") { continueDialogItem = null; onOpenDetailsManual(media) }
                    DialogOptionBtn("البدء من البداية") { continueDialogItem = null; onResumeContinue(p, true) }
                    DialogOptionBtn("تحديد كمشاهد") { vm.markAsWatched(media, p.season, p.episode); continueDialogItem = null }
                    DialogOptionBtn("إزالة", Color(0xFFFF6B6B)) { vm.removeProgress(p.key); continueDialogItem = null }
                }
            },
            confirmButton = {}
        )
    }

    cardMenuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cardMenuTarget = null },
            containerColor = Color(0xFF161922),
            title = { Text(target.displayTitle, color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogOptionBtn("التفاصيل") { cardMenuTarget = null; onOpenDetails(target) }
                    DialogOptionBtn("تغيير البوستر / الخلفية / الاسم") { val t = target; cardMenuTarget = null; artworkEditorTarget = t }
                    DialogOptionBtn("إضافة / إزالة من المكتبة") { vm.toggleLibrary(target); cardMenuTarget = null }
                    DialogOptionBtn("تحديد كمشاهد") { vm.markAsWatched(target, 1, 1); cardMenuTarget = null }
                }
            },
            confirmButton = {}
        )
    }

    artworkEditorTarget?.let { target -> CustomArtworkDialog(item = target, vm = vm, onDismiss = { artworkEditorTarget = null }) }
}

@Composable
private fun SidebarNavItem(icon: ImageVector, label: String, active: Boolean, expanded: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(if (focused) Color.White else if (active) Color(0xFF222736) else Color.Transparent)
            .border(if (focused) 2.5.dp else if (active) 1.5.dp else 0.dp, if (focused) Color.White else if (active) Color(0x77FFFFFF) else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        if (expanded) { Spacer(Modifier.width(12.dp)); Text(label, color = if (focused) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCard(progress: WatchProgressEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val remMin = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60000L)
    val ratio = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f) else 0.1f

    Column(
        modifier = Modifier.width(205.dp).clip(RoundedCornerShape(12.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF222736) else Color(0xFF141720))
            .onFocusChanged { focused = it.isFocused }.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(112.dp)) {
            FastTvImage(url = progress.backdropUrl.ifBlank { progress.posterUrl }, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0B0E)))))
            Text(if (progress.type == "series") "م${progress.season}:ح${progress.episode}" else "فيلم", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xBB000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            Text("متبقي $remMin د", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x44FFFFFF))) { Box(modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Color.White)) }
        }
        Text(progress.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterRow(title: String, items: List<MediaItem>, firstItemFocusRequester: FocusRequester? = null, expandOnFocus: Boolean = true, expandDelayMs: Long = 1200L, onFocus: (MediaItem) -> Unit, onClick: (MediaItem) -> Unit, onLongClick: (MediaItem) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                var focused by remember { mutableStateOf(false) }
                var expandedLandscape by remember { mutableStateOf(false) }
                LaunchedEffect(focused, expandOnFocus) {
                    if (focused && expandOnFocus) { delay(expandDelayMs); if (focused) expandedLandscape = true }
                    else expandedLandscape = false
                }
                val cardWidth by animateDpAsState(targetValue = if (expandedLandscape) 225.dp else 112.dp, label = "cardWidth")
                val baseMod = if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier

                Column(
                    modifier = baseMod.width(cardWidth).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(item) }.combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(12.dp)).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(12.dp)).background(Color(0xFF181B24))) {
                        FastTvImage(url = if (expandedLandscape) item.displayBackdrop else item.displayPoster, modifier = Modifier.fillMaxSize())
                        if (expandedLandscape) {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0A0B0E)))))
                            Text(item.displayTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                        }
                    }
                    if (!expandedLandscape) {
                        Spacer(Modifier.height(4.dp))
                        Text(item.displayTitle, color = if (focused) Color.White else Color(0xFFD0D4DC), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogOptionBtn(text: String, textColor: Color = Color.White, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (focused) Color.White else Color(0xFF222634))
            .border(if (focused) 2.5.dp else 0.dp, Color.White, RoundedCornerShape(8.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(text, color = if (focused) Color.Black else textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun CustomArtworkDialog(item: MediaItem, vm: FilmrViewModel, onDismiss: () -> Unit) {
    var options by remember { mutableStateOf(ArtworkOptions()) }
    var selectedPoster by remember { mutableStateOf(item.displayPoster) }
    var selectedBackdrop by remember { mutableStateOf(item.displayBackdrop) }
    var titleChoice by remember { mutableStateOf(item.displayTitle) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) { options = vm.loadArtworkGallery(item); loading = false }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF141720),
        title = { Text("تخصيص الغلاف والاسم (TMDB)", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (loading) Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("الاسم المعروض:", color = Color.LightGray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.titleAr.isNotBlank()) FilterChip(selected = titleChoice == item.titleAr, onClick = { titleChoice = item.titleAr }, label = { Text(item.titleAr) })
                        if (item.titleEn.isNotBlank()) FilterChip(selected = titleChoice == item.titleEn, onClick = { titleChoice = item.titleEn }, label = { Text(item.titleEn) })
                    }
                }
                if (options.posters.isNotEmpty()) item {
                    Text("اختر البوستر:", color = Color.LightGray, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options.posters) { url ->
                            var f by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.width(75.dp).height(110.dp).clip(RoundedCornerShape(8.dp)).border(if (selectedPoster == url || f) 3.dp else 0.dp, Color.White, RoundedCornerShape(8.dp)).onFocusChanged { f = it.isFocused }.clickable { selectedPoster = url }) { FastTvImage(url = url, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
                if (options.backdrops.isNotEmpty()) item {
                    Text("اختر الخلفية:", color = Color.LightGray, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options.backdrops) { url ->
                            var f by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.width(135.dp).height(78.dp).clip(RoundedCornerShape(8.dp)).border(if (selectedBackdrop == url || f) 3.dp else 0.dp, Color.White, RoundedCornerShape(8.dp)).onFocusChanged { f = it.isFocused }.clickable { selectedBackdrop = url }) { FastTvImage(url = url, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { vm.saveCustomArtwork(item.id, selectedPoster, selectedBackdrop, item.displayLogo, titleChoice); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("حفظ التغييرات", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.LightGray) } }
    )
}

@Composable
fun SearchScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focusReq.requestFocus() } catch (_: Exception) {} }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                OutlinedTextField(value = query, onValueChange = { query = it; if (it.length >= 2) scope.launch { results = vm.search(it) } }, placeholder = { Text("ابحث بالعربي أو الإنجليزي...", color = Color.Gray) }, modifier = Modifier.weight(1f).focusRequester(focusReq), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White))
                if (query.isNotBlank()) IconButton(onClick = { query = ""; results = emptyList() }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { it.id }) { item ->
                    var focused by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.onFocusChanged { focused = it.isFocused }.clickable { onOpenDetails(item) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))) { FastTvImage(url = item.displayPoster, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(4.dp))
                        Text(item.displayTitle, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val library by vm.library.collectAsState()
    val session by vm.session.collectAsState()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Text("المكتبة المتزامنة (${library.size})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (session != null) Button(onClick = { vm.syncAllStremioData(session!!.authKey) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("مزامنة Stremio") }
            }
            Spacer(Modifier.height(12.dp))
            if (library.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("المكتبة فارغة — سجل دخولك في Stremio أو أضف أعمالك من صفحة التفاصيل", color = Color.Gray) }
            else LazyVerticalGrid(columns = GridCells.Adaptive(112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library, key = { it.id }) { item ->
                    var focused by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.onFocusChanged { focused = it.isFocused }.clickable { onOpenDetails(item) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(162.dp).clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))) { FastTvImage(url = item.displayPoster, modifier = Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(4.dp))
                        Text(item.displayTitle, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val cal = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH) + 1) }
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)
    var entries by remember { mutableStateOf<List<CalendarEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(year, month) { loading = true; entries = vm.loadCalendarEntries(year, month); loading = false }
    val daysInMonth = remember(year, month) { Calendar.getInstance().apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Text("التقويم ومواعيد الحلقات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) { Icon(Icons.Filled.KeyboardArrowRight, null, tint = Color.White) }
                Text("$year / $month", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Color.White) }
            }
            Spacer(Modifier.height(10.dp))
            if (loading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..daysInMonth).toList()) { day ->
                    val dayEntries = entries.filter { it.day == day }
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.height(86.dp).clip(RoundedCornerShape(8.dp)).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF1F2330), RoundedCornerShape(8.dp)).background(if (focused) Color(0xFF222736) else Color(0xFF12151E)).onFocusChanged { focused = it.isFocused }.clickable(enabled = dayEntries.isNotEmpty()) { dayEntries.firstOrNull()?.let { onOpenDetails(it.item) } }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$day", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (day == todayDay) Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                        }
                        dayEntries.firstOrNull()?.let { entry ->
                            FastTvImage(url = entry.item.displayPoster, modifier = Modifier.width(34.dp).height(46.dp).clip(RoundedCornerShape(4.dp)))
                            Text("ح${entry.episode}", color = Color.LightGray, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
