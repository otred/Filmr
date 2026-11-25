package com.filmr.tv.ui
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import com.filmr.tv.viewmodel.FilmrViewModel
import kotlinx.coroutines.delay
@OptIn(UnstableApi::class)
@Composable
fun FilmrPlayerScreen(item: MediaItem, stream: UnifiedStream, season: Int, episode: Int, epTitle: String, vm: FilmrViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val accent = Color(settings.accentHex)
    var skipSegments by remember { mutableStateOf<List<SkipSegment>>(emptyList()) }
    var activeSkip by remember { mutableStateOf<SkipSegment?>(null) }
    var preparedSub by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(stream.id) {
        val subUrl = stream.subtitles.firstOrNull { it.lang.contains("ar", true) }?.url ?: stream.subtitles.firstOrNull()?.url
        if (!subUrl.isNullOrBlank()) preparedSub = ArabicSubtitleSanitizer.prepareSubtitle(ctx, subUrl)
        skipSegments = SkipAndTorrentEngine.fetchSkipSegments(item.imdbId, season, episode, settings.animeSkipClientId)
    }
    val player = remember(stream.url, preparedSub) {
        ExoPlayer.Builder(ctx).build().apply {
            val builder = MediaItem.Builder().setUri(stream.url)
            preparedSub?.let { uri ->
                builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("ar").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            }
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, skipSegments) {
        while (true) {
            delay(1000)
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0L)
            activeSkip = skipSegments.firstOrNull { pos in it.startMs..it.endMs }
            if (pos > 5000L && dur > 0L) vm.saveProgress(item, season, episode, epTitle, pos, dur)
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, modifier = Modifier.fillMaxSize())
        Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(10.dp))) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Text("${item.displayTitle}${if (item.type == "series") " • م$season:ح$episode" else ""}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        activeSkip?.let { seg ->
            Button(onClick = { player.seekTo(seg.endMs) }, colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                Icon(Icons.Default.FastForward, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(seg.label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
fun SearchScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by vm.searchResults.collectAsState()
    val settings by vm.settings.collectAsState()
    val accent = Color(settings.accentHex)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050507)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            OutlinedTextField(value = query, onValueChange = { query = it; if (it.length >= 2) vm.search(it) }, placeholder = { Text("ابحث عن فيلم أو مسلسل...") }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = accent))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.tmdbId }) { m ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF14161D)).clickable { onOpenDetails(m) }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = m.displayPoster, contentDescription = null, modifier = Modifier.width(64.dp).height(94.dp).clip(RoundedCornerShape(6.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.displayTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${if (m.type == "series") "مسلسل" else "فيلم"} • ${m.year} • ★ ${"%.1f".format(m.rating)}", color = Color(0xFFB0B3B8), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
@Composable
fun AddonsAndSyncScreen(vm: FilmrViewModel, onBack: () -> Unit) {
    val addons by vm.addons.collectAsState()
    val settings by vm.settings.collectAsState()
    val status by vm.statusMsg.collectAsState()
    val accent = Color(settings.accentHex)
    var urlInput by remember { mutableStateOf("") }
    var engineType by remember { mutableStateOf("Stremio") }
    var email by remember { mutableStateOf(settings.stremioEmail) }
    var pass by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF050507)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                Text("الإضافات ومزامنة Stremio & CloudStream", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (status.isNotBlank()) Text(status, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14161D))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مزامنة حساب Stremio", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.loginStremio(email, pass) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("تسجيل ومزامنة") }
                        if (settings.stremioAuthKey.isNotBlank()) OutlinedButton(onClick = { vm.syncStremioNow() }) { Text("تحديث الآن", color = Color.White) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14161D))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تثبيت إضافة جديدة", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = engineType == "Stremio", onClick = { engineType = "Stremio" }, label = { Text("Stremio Manifest") })
                        FilterChip(selected = engineType == "CloudStream", onClick = { engineType = "CloudStream" }, label = { Text("CloudStream Repo / .cs3") })
                    }
                    OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, placeholder = { Text("https://.../manifest.json أو رابط .cs3") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Button(onClick = { if (urlInput.isNotBlank()) { vm.installAddon(urlInput, engineType); urlInput = "" } }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("تثبيت الإضافة") }
                }
            }
        }
        items(addons, key = { it.id }) { ad ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14161D))) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ad.name} (${ad.engineType})", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(ad.description.ifBlank { ad.url }, color = Color(0xFF9EA3AE), fontSize = 12.sp, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = ad.enabled, onCheckedChange = { vm.toggleAddon(ad.id, it) })
                        IconButton(onClick = { vm.deleteAddon(ad.id) }) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFE57373)) }
                    }
                }
            }
        }
    }
}
@Composable
fun CalendarAndSettingsScreen(isCalendar: Boolean, vm: FilmrViewModel, onBack: () -> Unit, onOpenDetails: (MediaItem) -> Unit) {
    val library by vm.library.collectAsState()
    val settings by vm.settings.collectAsState()
    var mdbKey by remember { mutableStateOf(settings.mdbListApiKey) }
    var animeKey by remember { mutableStateOf(settings.animeSkipClientId) }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF050507)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text(if (isCalendar) "المكتبة والتقويم" else "الإعدادات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
item {
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { vm.profiles.forEach { p -> FilterChip(selected = vm.activeProfile.collectAsState().value.id == p.id, onClick = { vm.selectProfile(p) }, label = { Text(p.name) }) } }
OutlinedTextField(value = mdbKey, onValueChange = { mdbKey = it; vm.updateSettings { s -> s.copy(mdbListApiKey = it) } }, label = { Text("MDBList Key") }, singleLine = true)
OutlinedTextField(value = animeKey, onValueChange = { animeKey = it; vm.updateSettings { s -> s.copy(animeSkipClientId = it) } }, label = { Text("AnimeSkip ID") }, singleLine = true)
} } }
