package com.filmr.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.AddonEntity
import com.filmr.tv.data.UserSessionEntity
import com.filmr.tv.data.WatchHistoryEntity
import com.filmr.tv.model.*

@Composable
fun FilmrAppRoot(viewModel: FilmrViewModel) {
    var currentTab by remember { mutableStateOf("HOME") }
    val movies by viewModel.featuredMovies.collectAsState()
    val series by viewModel.featuredSeries.collectAsState()
    val history by viewModel.historyFlow.collectAsState()
    val addons by viewModel.addonsFlow.collectAsState()
    val session by viewModel.sessionFlow.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val artworkOptions by viewModel.artworkOptions.collectAsState()
    val streams by viewModel.streams.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val activeStream by viewModel.activeStream.collectAsState()
    val playerSettings by viewModel.playerSettings.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLoadingStreams by viewModel.isLoadingStreams.collectAsState()

    if (currentTab == "PLAYER" && activeStream != null) {
        DualPlayerScreen(
            stream = activeStream!!,
            availableSubtitles = subtitles,
            settings = playerSettings,
            onUpdateSettings = { viewModel.updatePlayerSettings(it) },
            onBack = { currentTab = "DETAILS" }
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF090B10))) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(92.dp),
            containerColor = Color(0xFF0D1119)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("فلمر", color = Color(0xFFE50914), fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(28.dp))

            NavigationRailItem(
                selected = currentTab == "HOME",
                onClick = { currentTab = "HOME" },
                icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية") },
                label = { Text("الرئيسية") }
            )
            NavigationRailItem(
                selected = currentTab == "SEARCH",
                onClick = { currentTab = "SEARCH" },
                icon = { Icon(Icons.Default.Search, contentDescription = "البحث") },
                label = { Text("البحث") }
            )
            NavigationRailItem(
                selected = currentTab == "ADDONS",
                onClick = { currentTab = "ADDONS" },
                icon = { Icon(Icons.Default.Extension, contentDescription = "الإضافات") },
                label = { Text("الإضافات") }
            )
            NavigationRailItem(
                selected = currentTab == "PROFILE",
                onClick = { currentTab = "PROFILE" },
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = "حسابي") },
                label = { Text("حسابي") }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (currentTab) {
                "HOME" -> HomeCatalogScreen(
                    movies = movies,
                    series = series,
                    history = history,
                    onSelectMedia = {
                        viewModel.openMediaDetails(it)
                        currentTab = "DETAILS"
                    }
                )
                "SEARCH" -> SearchScreen(
                    results = searchResults,
                    onSearch = { viewModel.searchAll(it) },
                    onSelectMedia = {
                        viewModel.openMediaDetails(it)
                        currentTab = "DETAILS"
                    }
                )
                "ADDONS" -> AddonsManagerScreen(
                    addons = addons,
                    onInstallUrl = { viewModel.installAddonOrRepo(it) },
                    onDeleteAddon = { viewModel.removeAddon(it) }
                )
                "PROFILE" -> ProfileAndAccountScreen(
                    session = session,
                    playerSettings = playerSettings,
                    onLoginStremio = { e, p -> viewModel.loginWithStremio(e, p) },
                    onLogout = { viewModel.logoutStremio() },
                    onUpdateAvatar = { viewModel.updateCustomProfileAvatar(it) },
                    onSaveTmdbKey = { viewModel.saveTmdbApiKey(it) },
                    onUpdateSettings = { viewModel.updatePlayerSettings(it) }
                )
                "DETAILS" -> selectedMedia?.let { media ->
                    MediaDetailScreen(
                        media = media,
                        artworkOptions = artworkOptions,
                        streams = streams,
                        isLoadingStreams = isLoadingStreams,
                        onLoadStreams = { vid, s, e -> viewModel.loadStreamsForVideo(media, vid, s, e) },
                        onPlayStream = { st, s, e ->
                            viewModel.startPlayback(st, media, s, e)
                            currentTab = "PLAYER"
                        },
                        onSaveArtwork = { p, b, l -> viewModel.selectCustomArtwork(p, b, l) },
                        onBack = { currentTab = "HOME" }
                    )
                }
            }

            statusMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearStatus() }) { Text("إغلاق", color = Color(0xFFE50914)) }
                    }
                ) { Text(msg) }
            }
        }
    }
}

@Composable
private fun HomeCatalogScreen(
    movies: List<MediaItem>,
    series: List<MediaItem>,
    history: List<WatchHistoryEntity>,
    onSelectMedia: (MediaItem) -> Unit
) {
    val hero = movies.firstOrNull()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
        if (hero != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    AsyncImage(
                        model = hero.backdropUrl,
                        contentDescription = hero.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF090B10)))
                        )
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(hero.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text("⭐ ${hero.rating} • ${hero.year}", color = Color(0xFFF5C518), fontWeight = FontWeight.Bold)
                        Text(hero.overview, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.65f))
                        Button(
                            onClick = { onSelectMedia(hero) },
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("شاهد الآن", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                MediaSectionRow(
                    title = "متابعة المشاهدة",
                    items = history.map {
                        MediaItem(id = it.mediaId, type = it.type, title = it.title, posterUrl = it.posterUrl, backdropUrl = it.backdropUrl)
                    },
                    onSelect = onSelectMedia
                )
            }
        }

        item { MediaSectionRow(title = "أفلام رائجة", items = movies, onSelect = onSelectMedia) }
        item { MediaSectionRow(title = "مسلسلات مختارة", items = series, onSelect = onSelectMedia) }
    }
}

@Composable
private fun MediaSectionRow(title: String, items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                Card(
                    onClick = { onSelect(item) },
                    modifier = Modifier.width(155.dp).height(230.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaDetailScreen(
    media: MediaItem,
    artworkOptions: ArtworkOptions,
    streams: List<UnifiedStream>,
    isLoadingStreams: Boolean,
    onLoadStreams: (String, Int?, Int?) -> Unit,
    onPlayStream: (UnifiedStream, Int, Int) -> Unit,
    onSaveArtwork: (String?, String?, String?) -> Unit,
    onBack: () -> Unit
) {
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var showArtworkPicker by remember { mutableStateOf(false) }

    LaunchedEffect(media.id) {
        val initialVid = if (media.type == "series") "${media.id}:1:1" else media.id
        onLoadStreams(initialVid, 1, 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = media.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090B10).copy(alpha = 0.86f)))

        LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onBack) { Text("رجوع") }
                    FilledTonalButton(onClick = { showArtworkPicker = true }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تغيير البوستر / الخلفية / الشعار")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AsyncImage(
                        model = media.posterUrl,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(170.dp).height(250.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (media.logoUrl != null) {
                            AsyncImage(model = media.logoUrl, contentDescription = media.title, modifier = Modifier.height(80.dp))
                        } else {
                            Text(media.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                        Text("⭐ ${media.rating} • ${media.year}", color = Color(0xFFF5C518), fontWeight = FontWeight.Bold)
                        Text(media.overview, color = Color.LightGray, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (media.type == "series" && media.seasons.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(media.seasons) { season ->
                            FilterChip(
                                selected = selectedSeason == season.seasonNumber,
                                onClick = { selectedSeason = season.seasonNumber },
                                label = { Text(season.title) }
                            )
                        }
                    }
                }
                val currentEpisodes = media.seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodes ?: emptyList()
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentEpisodes) { ep ->
                            FilterChip(
                                selected = selectedEpisode == ep.episode,
                                onClick = {
                                    selectedEpisode = ep.episode
                                    onLoadStreams("${media.id}:$selectedSeason:${ep.episode}", selectedSeason, ep.episode)
                                },
                                label = { Text("حلقة ${ep.episode}: ${ep.title}") }
                            )
                        }
                    }
                }
            }

            item {
                Text("مصادر المشاهدة المتاحة (Stremio & CloudStream)", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            if (isLoadingStreams) {
                item { CircularProgressIndicator(color = Color(0xFFE50914)) }
            }

            items(streams) { st ->
                Card(
                    onClick = { onPlayStream(st, selectedSeason, selectedEpisode) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(st.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("المصدر: ${st.sourceName} (${st.sourceType.name})", color = Color(0xFFE50914), fontSize = 13.sp)
                        }
                        Badge(containerColor = Color(0xFF232A38)) {
                            Text("${st.qualityScore}p", color = Color.White, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }

        if (showArtworkPicker) {
            AlertDialog(
                onDismissRequest = { showArtworkPicker = false },
                confirmButton = { TextButton(onClick = { showArtworkPicker = false }) { Text("تم") } },
                title = { Text("تخصيص صور العمل بحرية") },
                text = {
                    var customUrlInput by remember { mutableStateOf("") }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("أو الصق رابط أي صورة خارجية مباشرة") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (customUrlInput.isNotBlank()) onSaveArtwork(customUrlInput, null, null) }) { Text("حفظ كبوستر") }
                            Button(onClick = { if (customUrlInput.isNotBlank()) onSaveArtwork(null, customUrlInput, null) }) { Text("حفظ كخلفية") }
                            Button(onClick = { if (customUrlInput.isNotBlank()) onSaveArtwork(null, null, customUrlInput) }) { Text("حفظ كشعار") }
                        }
                        if (artworkOptions.posters.isNotEmpty()) {
                            Text("البوسترات المتاحة من TMDB:")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(artworkOptions.posters.take(15)) { pUrl ->
                                    AsyncImage(
                                        model = pUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp, 120.dp).clickable { onSaveArtwork(pUrl, null, null) }
                                    )
                                }
                            }
                        }
                        if (artworkOptions.backdrops.isNotEmpty()) {
                            Text("الخلفيات المتاحة من TMDB:")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(artworkOptions.backdrops.take(15)) { bUrl ->
                                    AsyncImage(
                                        model = bUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(140.dp, 80.dp).clickable { onSaveArtwork(null, bUrl, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileAndAccountScreen(
    session: UserSessionEntity?,
    playerSettings: PlayerSettings,
    onLoginStremio: (String, String) -> Unit,
    onLogout: () -> Unit,
    onUpdateAvatar: (String) -> Unit,
    onSaveTmdbKey: (String) -> Unit,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var avatarInput by remember { mutableStateOf(session?.customAvatarUrl ?: "") }
    var tmdbInput by remember { mutableStateOf(session?.tmdbApiKey ?: "") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("حساب Stremio والملف الشخصي", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        if (session != null && session.authKey.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))) {
                    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = session.customAvatarUrl ?: session.avatarUrl ?: "https://www.stremio.com/website/stremio-logo-small.png",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.fullName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(session.email, color = Color.LightGray)
                            Text("حالة المزامنة: متصل بحساب Stremio السحابي ✅", color = Color(0xFF4ADE80), fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = onLogout) { Text("تسجيل الخروج") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = avatarInput,
                        onValueChange = { avatarInput = it },
                        label = { Text("رابط صورة مخصصة لملفك الشخصي") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onUpdateAvatar(avatarInput) }) { Text("حفظ الصورة") }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ربط حساب Stremio لمزامنة جميع إضافاتك ومكتبتك تلقائياً", color = Color.White, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = emailInput, onValueChange = { emailInput = it }, label = { Text("البريد الإلكتروني لحساب Stremio") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = passInput, onValueChange = { passInput = it }, label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = { onLoginStremio(emailInput, passInput) },
                        ) { Text("تسجيل الدخول ومزامنة الإضافات", color = Color.White) }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إعدادات المشغل الافتراضية", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("مدة التقديم والترجيع الافتراضية بالريموت:", color = Color.LightGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = playerSettings.seekStepSeconds == sec,
                                onClick = { onUpdateSettings { it.copy(seekStepSeconds = sec) } },
                                label = { Text("$sec ثواني") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        label = { Text("مفتاح TMDB API (لتفعيل البحث العربي وتغيير البوسترات والشعارات)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { onSaveTmdbKey(tmdbInput) }) { Text("حفظ مفتاح TMDB") }
                }
            }
        }
    }
}

@Composable
private fun AddonsManagerScreen(
    addons: List<AddonEntity>,
    onInstallUrl: (String) -> Unit,
    onDeleteAddon: (AddonEntity) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("إدارة إضافات Stremio ومستودعات CloudStream", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("الصق رابط manifest.json لستريمو أو repo.json لكلاود ستريم") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (urlInput.isNotBlank()) { onInstallUrl(urlInput); urlInput = "" } },
            ) { Text("تثبيت الإضافة", color = Color.White) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(addons) { addon ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${addon.name} [${addon.type}]", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(addon.description, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDeleteAddon(addon) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFE50914))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    results: List<MediaItem>,
    onSearch: (String) -> Unit,
    onSelectMedia: (MediaItem) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            label = { Text("ابحث عن فيلم، مسلسل، أو أنمي...") },
            modifier = Modifier.fillMaxWidth()
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(155.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(results) { item ->
                Card(onClick = { onSelectMedia(item) }, modifier = Modifier.height(230.dp)) {
                    AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
