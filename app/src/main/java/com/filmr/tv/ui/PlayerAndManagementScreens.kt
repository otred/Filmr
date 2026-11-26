package com.filmr.tv.ui

import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.TypedValue
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilmrPlayerScreen(
    item: MediaItem,
    initialStream: UnifiedStream,
    season: Int,
    episode: Int,
    epTitle: String,
    startPositionMs: Long = 0L,
    vm: FilmrViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    var currentStream by remember { mutableStateOf(initialStream) }
    var activeSubUrl by remember { mutableStateOf<String?>(null) }
    var preparedSub by remember { mutableStateOf<Uri?>(null) }
    var availableSubs by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var allStreams by remember { mutableStateOf<List<UnifiedStream>>(listOf(initialStream)) }
    var skipSegments by remember { mutableStateOf<List<SkipSegment>>(emptyList()) }

    var showControls by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var activePanel by remember { mutableStateOf("none") }

    var boostPercent by remember { mutableIntStateOf(settings.volumeBoostPercent) }
    var subSizeSp by remember { mutableFloatStateOf(settings.subtitleSizeSp) }
    var subColorHex by remember { mutableLongStateOf(settings.subtitleColorHex) }
    var subDelayMs by remember { mutableLongStateOf(0L) }
    var currentPosMs by remember { mutableLongStateOf(startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }

    val playableUrl = remember(currentStream.url) {
        if (currentStream.url.startsWith("magnet:")) "http://127.0.0.1:8090/stream/fname?link=${Uri.encode(currentStream.url)}&index=1&play"
        else currentStream.url
    }

    LaunchedEffect(currentStream.url) {
        val subs = currentStream.subtitles + vm.fetchSubtitles(item, season, episode)
        availableSubs = subs
        val arSub = subs.firstOrNull { it.lang.contains("ar", true) }?.url
        activeSubUrl = arSub
        if (!arSub.isNullOrBlank()) preparedSub = ArabicSubtitleSanitizer.prepareSubtitle(ctx, arSub, subDelayMs)
        skipSegments = SkipAndTorrentEngine.fetchSkipSegments(item.imdbId, season, episode, if (settings.animeSkipEnabled) settings.animeSkipClientId else "")
        allStreams = vm.fetchStreams(item, season, episode).ifEmpty { listOf(currentStream) }
    }

    LaunchedEffect(activeSubUrl, subDelayMs) {
        activeSubUrl?.takeIf { it.isNotBlank() }?.let { preparedSub = ArabicSubtitleSanitizer.prepareSubtitle(ctx, it, subDelayMs) }
    }

    val player = remember(playableUrl, preparedSub) {
        ExoPlayer.Builder(ctx).build().apply {
            val builder = androidx.media3.common.MediaItem.Builder().setUri(playableUrl)
            preparedSub?.let { uri ->
                builder.setSubtitleConfigurations(listOf(androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("ar").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            }
            setMediaItem(builder.build())
            prepare()
            if (currentPosMs > 0L) seekTo(currentPosMs)
            playWhenReady = true
        }
    }

    LaunchedEffect(player, boostPercent) {
        try {
            val enhancer = LoudnessEnhancer(player.audioSessionId)
            enhancer.setTargetGain(boostPercent * 25)
            enhancer.enabled = boostPercent > 0
        } catch (_: Exception) { }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            currentPosMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            if (showControls && activePanel == "none" && System.currentTimeMillis() - lastInteractionMs > 4000L) showControls = false
            if (durationMs > 0L && (currentPosMs / 1000L) % 5L == 0L) vm.saveProgress(item, season, episode, currentPosMs, durationMs, currentStream.url, currentStream.addonName)
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val nowStr = timeFmt.format(Date())
    val endTimeStr = if (durationMs > currentPosMs) timeFmt.format(Date(System.currentTimeMillis() + (durationMs - currentPosMs))) else "--:--"

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { showControls = true; lastInteractionMs = System.currentTimeMillis(); false }.clickable { showControls = !showControls; lastInteractionMs = System.currentTimeMillis() }
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { this.player = player; this.useController = false } },
            update = { view ->
                view.subtitleView?.apply {
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subSizeSp)
                    setStyle(CaptionStyleCompat(Color(subColorHex).toArgb(), Color(0xAA000000).toArgb(), android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val activeSkip = skipSegments.firstOrNull { currentPosMs in it.startMs..it.endMs }
        if (activeSkip != null) {
            LaunchedEffect(activeSkip) { if (settings.autoSkipIntro) player.seekTo(activeSkip.endMs) }
            Button(onClick = { player.seekTo(activeSkip.endMs) }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("تخطي ${activeSkip.label}", fontWeight = FontWeight.Bold)
            }
        }

        if (showControls) {
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0x99000000)).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(nowStr, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("ينتهي $endTimeStr", color = Color.LightGray, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.displayTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (item.type == "series") Text("الموسم $season • الحلقة $episode", color = Color.LightGray, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onBack, modifier = Modifier.background(Color(0x44FFFFFF), CircleShape)) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            }

            Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xBB000000)).padding(horizontal = 20.dp, vertical = 14.dp)) {
                val progressRatio = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(value = progressRatio, onValueChange = { r -> if (durationMs > 0) player.seekTo((r * durationMs).toLong()) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerHudBtn(if (player.isPlaying) "إيقاف" else "تشغيل", if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow) { if (player.isPlaying) player.pause() else player.play() }
                    PlayerHudBtn("-${settings.seekStepSeconds}s", Icons.Filled.Replay10) { player.seekTo((player.currentPosition - settings.seekStepSeconds * 1000L).coerceAtLeast(0L)) }
                    PlayerHudBtn("+${settings.seekStepSeconds}s", Icons.Filled.Forward10) { player.seekTo(player.currentPosition + settings.seekStepSeconds * 1000L) }
                    Text("${formatMs(currentPosMs)} / ${formatMs(durationMs)}", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    PlayerHudBtn("الصوت", Icons.Filled.VolumeUp) { activePanel = if (activePanel == "audio") "none" else "audio" }
                    PlayerHudBtn("الترجمة", Icons.Filled.Subtitles) { activePanel = if (activePanel == "subs") "none" else "subs" }
                    PlayerHudBtn("المصادر", Icons.Filled.Tune) { activePanel = if (activePanel == "sources") "none" else "sources" }
                    if (currentStream.url.startsWith("magnet:")) PlayerHudBtn("مشغل خارجي", Icons.Filled.OpenInNew) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentStream.url))) } catch (_: Exception) { } }
                }
            }
        }

        if (activePanel != "none") {
            Column(modifier = Modifier.align(Alignment.CenterEnd).width(340.dp).fillMaxHeight().background(Color(0xEE141720)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (activePanel) { "audio" -> "الصوت والتضخيم"; "subs" -> "الترجمة ومزامنة السطر"; else -> "المصادر المتاحة" }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { activePanel = "none" }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
                }
                when (activePanel) {
                    "audio" -> {
                        Text("تضخيم الصوت (Volume Boost): +$boostPercent%", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 50, 100, 150, 200).forEach { b -> FilterChip(selected = boostPercent == b, onClick = { boostPercent = b }, label = { Text("+$b%") }) }
                        }
                    }
                    "subs" -> {
                        Text("التأخير: ${subDelayMs / 1000.0}s", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { subDelayMs -= 500L }) { Text("-0.5s", color = Color.White) }
                            OutlinedButton(onClick = { subDelayMs += 500L }) { Text("+0.5s", color = Color.White) }
                            Button(onClick = { subDelayMs = 0L }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("مزامنة السطر", fontWeight = FontWeight.Bold) }
                        }
                        Text("حجم الترجمة: ${subSizeSp.toInt()}", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(16f, 20f, 24f, 28f).forEach { sz -> FilterChip(selected = subSizeSp == sz, onClick = { subSizeSp = sz }, label = { Text("${sz.toInt()}") }) }
                        }
                        Text("لون النص:", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFFFFFFFF to "أبيض", 0xFFFFEB3B to "أصفر", 0xFF4DD0E1 to "سماوي").forEach { (hex, lbl) -> FilterChip(selected = subColorHex == hex, onClick = { subColorHex = hex }, label = { Text(lbl) }) }
                        }
                        Text("ملفات الترجمة (${availableSubs.size}):", color = Color.LightGray, fontSize = 12.sp)
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableSubs) { sub ->
                                Surface(onClick = { activeSubUrl = sub.url; scope.launch { preparedSub = ArabicSubtitleSanitizer.prepareSubtitle(ctx, sub.url, subDelayMs) } }, color = if (activeSubUrl == sub.url) Color.White else Color(0xFF222634), shape = RoundedCornerShape(8.dp)) {
                                    Text(sub.label, color = if (activeSubUrl == sub.url) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(10.dp))
                                }
                            }
                        }
                    }
                    "sources" -> {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allStreams) { st ->
                                Surface(onClick = { currentStream = st; activePanel = "none" }, color = if (st.url == currentStream.url) Color.White else Color(0xFF1D212D), shape = RoundedCornerShape(8.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(st.name, color = if (st.url == currentStream.url) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(st.title, color = if (st.url == currentStream.url) Color.DarkGray else Color.LightGray, fontSize = 11.sp, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHudBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier = Modifier.clip(CircleShape).background(if (focused) Color.White else Color(0x44FFFFFF)).border(if (focused) 2.5.dp else 0.dp, Color.White, CircleShape).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
fun AddonsAndSyncScreen(vm: FilmrViewModel, onBack: () -> Unit) {
    val addons by vm.addons.collectAsState()
    var section by remember { mutableStateOf("stremio") }
    var inputUrl by remember { mutableStateOf("") }
    val stremioList = addons.filter { it.type == "stremio" }
    val cloudList = addons.filter { it.type == "cloudstream" }
    val activeList = if (section == "stremio") stremioList else cloudList

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                    Text("الإضافات والملحقات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.updateAllAddons() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("تحديث الإضافات", fontWeight = FontWeight.Bold) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = section == "stremio", onClick = { section = "stremio" }, label = { Text("إضافات Stremio (${stremioList.size})") })
                    FilterChip(selected = section == "cloudstream", onClick = { section = "cloudstream" }, label = { Text("ملحقات CloudStream (${cloudList.size})") })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = inputUrl, onValueChange = { inputUrl = it }, placeholder = { Text(if (section == "stremio") "ألصق رابط manifest.json لإضافة Stremio..." else "ألصق رابط مستودع CloudStream (repo.json)...", color = Color.Gray) }, modifier = Modifier.weight(1f), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White))
                    Button(onClick = { if (inputUrl.isNotBlank()) { if (section == "stremio") vm.addStremioAddon(inputUrl) else vm.addCloudStreamRepo(inputUrl); inputUrl = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("إضافة", fontWeight = FontWeight.Bold) }
                }
            }
            items(activeList, key = { it.id }) { addon ->
                var focused by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF222634), RoundedCornerShape(10.dp)).background(Color(0xFF141720), RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(addon.description.ifBlank { addon.url }, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                    }
                    Switch(checked = addon.isEnabled, onCheckedChange = { vm.toggleAddon(addon) })
                    IconButton(onClick = { vm.deleteAddon(addon.id) }) { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B)) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: FilmrViewModel, onBack: () -> Unit, onOpenAddons: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val session by vm.session.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val addons by vm.addons.collectAsState()
    val library by vm.library.collectAsState()
    val partyCode by vm.watchPartyCode.collectAsState()

    var selectedTab by remember { mutableStateOf("account") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var newProfileName by remember { mutableStateOf("") }
    var joinPartyInput by remember { mutableStateOf("") }
    var mdbKey by remember { mutableStateOf(settings.mdbListApiKey) }
    var animeKey by remember { mutableStateOf(settings.animeSkipClientId) }

    val tabs = listOf("account" to "الحساب والمزامنة", "profiles" to "المستخدمون (5)", "watchparty" to "Watch Party", "appearance" to "المظهر", "layout" to "واجهة العرض", "content" to "المحتوى والاكتشاف", "playback" to "وسائط التشغيل", "services" to "الخدمات المرتبطة والتتبع")

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier = Modifier.fillMaxSize().background(if (settings.amoledBlack) Color.Black else Color(0xFF0A0B0E)).padding(16.dp)) {
            Column(modifier = Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                    Text("الإعدادات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                tabs.forEach { (id, label) ->
                    var focused by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selectedTab == id) Color.White else if (focused) Color(0xFF282D3D) else Color(0xFF141720)).border(if (focused) 2.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.clickable { selectedTab = id }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(label, color = if (selectedTab == id) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF11141C), RoundedCornerShape(14.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedTab) {
                    "account" -> {
                        item { Text("الحساب وحالة المزامنة (Stremio)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item {
                            if (session != null) {
                                Text("الحساب المتصل: ${session!!.email}", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("الإضافات المتزامنة: ${addons.size} • عناصر المكتبة: ${library.size}", color = Color.LightGray, fontSize = 13.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { vm.syncAllStremioData(session!!.authKey) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("مزامنة كاملة الآن", fontWeight = FontWeight.Bold) }
                                    OutlinedButton(onClick = { vm.logoutStremio() }) { Text("تسجيل الخروج", color = Color(0xFFFF6B6B)) }
                                }
                            } else {
                                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني لحساب Stremio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { vm.loginStremio(email, pass) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("تسجيل الدخول ومزامنة الإضافات والمكتبة", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    "profiles" -> {
                        item { Text("إدارة الملفات الشخصية (حتى 5 مستخدمين)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        items(profiles) { p ->
                            var focused by remember { mutableStateOf(false) }
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0xFF242938), RoundedCornerShape(10.dp)).background(if (activeProfile.id == p.id) Color(0xFF282E3E) else Color(0xFF181C26)).onFocusChanged { focused = it.isFocused }.clickable { vm.selectProfile(p) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccountCircle, null, tint = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (activeProfile.id == p.id) Text("نشط حالياً", color = Color(0xFF81C784), fontSize = 12.sp)
                            }
                        }
                        if (profiles.size < 5) item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, label = { Text("اسم المستخدم الجديد") }, modifier = Modifier.weight(1f), singleLine = true)
                                Button(onClick = { vm.addProfile(newProfileName); newProfileName = "" }) { Text("إضافة") }
                            }
                        }
                    }
                    "watchparty" -> {
                        item { Text("المشاهدة الجماعية (Watch Party)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Button(onClick = { vm.createWatchPartyCode() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("إنشاء غرفة جديدة", fontWeight = FontWeight.Bold) }
                            if (partyCode.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("رمز غرفتك الحالي: $partyCode", color = Color(0xFF81C784), fontSize = 18.sp, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = joinPartyInput, onValueChange = { joinPartyInput = it }, label = { Text("أدخل رمز غرفة صديقك (6 أحرف)") }, modifier = Modifier.weight(1f), singleLine = true)
                                Button(onClick = { vm.setWatchPartyCode(joinPartyInput) }) { Text("انضمام") }
                            }
                        }
                    }
                    "appearance" -> {
                        item { Text("المظهر والألوان", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(0xFFFFFFFF to "أبيض", 0xFFE50914 to "قرمزي", 0xFF38BDF8 to "محيطي", 0xFFA855F7 to "بنفسجي", 0xFF10B981 to "زمردي").forEach { (hex, lbl) ->
                                    FilterChip(selected = settings.accentHex == hex, onClick = { vm.updateSettings { it.copy(accentHex = hex) } }, label = { Text(lbl) })
                                }
                            }
                        }
                        item { SettingToggleRow("أسطح سوداء حالكة (AMOLED)", settings.amoledBlack) { v -> vm.updateSettings { it.copy(amoledBlack = v) } } }
                        item { SettingToggleRow("تفضيل العناوين الإنجليزية بدل العربية", settings.preferEnglishTitles) { v -> vm.updateSettings { it.copy(preferEnglishTitles = v) } } }
                    }
                    "layout" -> {
                        item { Text("إعدادات تنسيق الصفحة الرئيسية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item { SettingToggleRow("تكبير البطاقة المحددة لتصبح أفقية", settings.expandFocusedCard) { v -> vm.updateSettings { it.copy(expandFocusedCard = v) } } }
                        item { SettingToggleRow("إظهار صف متابعة المشاهدة", settings.showContinueWatching) { v -> vm.updateSettings { it.copy(showContinueWatching = v) } } }
                        item { SettingToggleRow("إظهار صف الأفلام الرائجة", settings.trendingMoviesRow) { v -> vm.updateSettings { it.copy(trendingMoviesRow = v) } } }
                        item { SettingToggleRow("إظهار صف المسلسلات الرائجة", settings.trendingSeriesRow) { v -> vm.updateSettings { it.copy(trendingSeriesRow = v) } } }
                        item { SettingToggleRow("إظهار صف Disney+", settings.disneyRow) { v -> vm.updateSettings { it.copy(disneyRow = v) } } }
                        item { SettingToggleRow("إظهار صف HBO", settings.hboRow) { v -> vm.updateSettings { it.copy(hboRow = v) } } }
                    }
                    "content" -> {
                        item { Text("المحتوى والاكتشاف", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item { Button(onClick = onOpenAddons, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("إدارة إضافات Stremio وملحقات CloudStream", fontWeight = FontWeight.Bold) } }
                    }
                    "playback" -> {
                        item { Text("وسائط التشغيل والصوت والتورنت", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item { SettingToggleRow("تشغيل ملفات التورنت (Torrentio / P2P)", settings.torrentEnabled) { v -> vm.updateSettings { it.copy(torrentEnabled = v) } } }
                        item { SettingToggleRow("تشغيل الحلقة التالية تلقائياً (Up Next)", settings.autoPlayNext) { v -> vm.updateSettings { it.copy(autoPlayNext = v) } } }
                        item { SettingToggleRow("تخطي المقدمات تلقائياً (Skip Intro)", settings.autoSkipIntro) { v -> vm.updateSettings { it.copy(autoSkipIntro = v) } } }
                        item { SettingToggleRow("تخطي الملخصات تلقائياً (Skip Recap)", settings.autoSkipRecap) { v -> vm.updateSettings { it.copy(autoSkipRecap = v) } } }
                        item { SettingToggleRow("تخطي شارة النهاية تلقائياً (End Credits)", settings.autoSkipCredits) { v -> vm.updateSettings { it.copy(autoSkipCredits = v) } } }
                        item {
                            Text("مقدار التقديم والترجيع: ${settings.seekStepSeconds} ثوانٍ", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 10, 15, 30).forEach { sec -> FilterChip(selected = settings.seekStepSeconds == sec, onClick = { vm.updateSettings { it.copy(seekStepSeconds = sec) } }, label = { Text("${sec}s") }) }
                            }
                        }
                    }
                    "services" -> {
                        item { Text("الخدمات المرتبطة والتتبع", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        item { SettingToggleRow("إثراء بيانات TMDB بالعربية", settings.tmdbEnrichmentEnabled) { v -> vm.updateSettings { it.copy(tmdbEnrichmentEnabled = v) } } }
                        item { SettingToggleRow("الترجمة التلقائية للأوصاف غير المعرّبة", settings.autoTranslateArabic) { v -> vm.updateSettings { it.copy(autoTranslateArabic = v) } } }
                        item { SettingToggleRow("تفعيل تقييمات MDBList", settings.mdbListEnabled) { v -> vm.updateSettings { it.copy(mdbListEnabled = v) } } }
                        item { OutlinedTextField(value = mdbKey, onValueChange = { mdbKey = it; vm.updateSettings { s -> s.copy(mdbListApiKey = it) } }, label = { Text("MDBList API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item { SettingToggleRow("تفعيل Anime-Skip لتخطي الأنمي", settings.animeSkipEnabled) { v -> vm.updateSettings { it.copy(animeSkipEnabled = v) } } }
                        item { OutlinedTextField(value = animeKey, onValueChange = { animeKey = it; vm.updateSettings { s -> s.copy(animeSkipClientId = it) } }, label = { Text("Anime-Skip Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item { SettingToggleRow("مزامنة سجل المشاهدة مع Trakt", settings.traktConnected) { v -> vm.updateSettings { it.copy(traktConnected = v) } } }
                        item { SettingToggleRow("مزامنة القوائم مع Simkl", settings.simklConnected) { v -> vm.updateSettings { it.copy(simklConnected = v) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(if (focused) 2.5.dp else 1.dp, if (focused) Color.White else Color(0xFF202534), RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF222736) else Color(0xFF171B26)).onFocusChanged { focused = it.isFocused }.clickable { onCheckedChange(!checked) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
