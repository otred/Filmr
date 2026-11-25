package com.filmr.tv.ui

import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FilmrPlayerScreen(
    item: MediaItem,
    stream: UnifiedStream,
    season: Int,
    episode: Int,
    epTitle: String,
    vm: FilmrViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    var preparedSub by remember { mutableStateOf<Uri?>(null) }
    var skipSegments by remember { mutableStateOf<List<SkipSegment>>(emptyList()) }

    LaunchedEffect(stream.url) {
        val subUrl = stream.subtitles.firstOrNull { it.lang.contains("ar", true) }?.url
            ?: vm.fetchSubtitles(item, season, episode).firstOrNull { it.lang.contains("ar", true) }?.url
        if (!subUrl.isNullOrBlank()) preparedSub = ArabicSubtitleSanitizer.prepareSubtitle(ctx, subUrl)
        skipSegments = SkipAndTorrentEngine.fetchSkipSegments(item.imdbId, season, episode, settings.animeSkipClientId)
    }

    val player = remember(stream.url, preparedSub) {
        ExoPlayer.Builder(ctx).build().apply {
            val builder = androidx.media3.common.MediaItem.Builder().setUri(stream.url)
            preparedSub?.let { uri ->
                builder.setSubtitleConfigurations(
                    listOf(
                        androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(uri)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("ar")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
            }
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.duration > 0) vm.saveProgress(item, season, episode, player.currentPosition, player.duration)
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxSize())
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color(0x88000000), RoundedCornerShape(50))) {
            Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
        }
        val activeSkip = skipSegments.firstOrNull { player.currentPosition in it.startMs..it.endMs }
        if (activeSkip != null) {
            Button(
                onClick = { player.seekTo(activeSkip.endMs) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(settings.accentHex))
            ) {
                Text("تخطي ${activeSkip.label}")
            }
        }
    }
}

@Composable
fun SearchScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) scope.launch { results = vm.search(it) }
                },
                placeholder = { Text("ابحث عن فيلم أو مسلسل...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF14161D)).clickable { onOpenDetails(item) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = item.displayPoster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(item.displayTitle, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(item.year, color = Color.Gray, fontSize = 12.sp)
                    }
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
    var repoUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Text("الإضافات ومزامنة Stremio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF14161D), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حساب Stremio", color = Color.White, fontWeight = FontWeight.Bold)
                if (session != null) {
                    Text("مسجل كـ: ${session!!.email}", color = Color.Green)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.fetchUserAddonsFromAccount(session!!.authKey) }) { Text("مزامنة الإضافات") }
                        OutlinedButton(onClick = { vm.logoutStremio() }) { Text("تسجيل خروج") }
                    }
                } else {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true)
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("كلمة المرور") }, singleLine = true)
                    Button(onClick = { vm.loginStremio(email, pass) }) { Text("تسجيل الدخول") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = addonUrl, onValueChange = { addonUrl = it }, label = { Text("رابط إضافة Stremio") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = { if (addonUrl.isNotBlank()) { vm.addAddon(addonUrl); addonUrl = "" } }) { Text("إضافة") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = repoUrl, onValueChange = { repoUrl = it }, label = { Text("رابط مستودع CloudStream") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = { if (repoUrl.isNotBlank()) { vm.syncCloudStreamRepo(repoUrl); repoUrl = "" } }) { Text("جلب") }
            }
        }
        items(addons) { addon ->
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF14161D), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(addon.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(addon.description, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
                Switch(checked = addon.isEnabled, onCheckedChange = { vm.toggleAddon(addon) })
                IconButton(onClick = { vm.deleteAddon(addon.id) }) { Icon(Icons.Filled.Delete, null, tint = Color.Red) }
            }
        }
    }
}

@Composable
fun CalendarAndSettingsScreen(isCalendar: Boolean, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val settings by vm.settings.collectAsState()
    val library by vm.library.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    var mdbKey by remember { mutableStateOf(settings.mdbListApiKey) }
    var animeKey by remember { mutableStateOf(settings.animeSkipClientId) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Text(if (isCalendar) "الجدول والمكتبة" else "الإعدادات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (isCalendar) {
            items(library) { item ->
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF14161D), RoundedCornerShape(10.dp)).clickable { onOpenDetails(item) }.padding(12.dp)) {
                    Text(item.displayTitle, color = Color.White)
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.profiles.forEach { p ->
                        FilterChip(selected = activeProfile.id == p.id, onClick = { vm.selectProfile(p) }, label = { Text(p.name) })
                    }
                }
            }
            item {
                OutlinedTextField(value = mdbKey, onValueChange = { mdbKey = it; vm.updateSettings { s -> s.copy(mdbListApiKey = it) } }, label = { Text("MDBList Key") }, singleLine = true)
            }
            item {
                OutlinedTextField(value = animeKey, onValueChange = { animeKey = it; vm.updateSettings { s -> s.copy(animeSkipClientId = it) } }, label = { Text("AnimeSkip ID") }, singleLine = true)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("شاشة سوداء بالكامل (AMOLED)", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = settings.amoledBlack, onCheckedChange = { v -> vm.updateSettings { it.copy(amoledBlack = v) } })
                }
            }
        }
    }
}
