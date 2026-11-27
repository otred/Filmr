package com.filmr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.components.QrCodeImage
import com.filmr.tv.ui.theme.*

enum class SettingsTab(val titleAr: String, val icon: ImageVector) {
    ACCOUNT("الحساب والمزامنة", Icons.Rounded.AccountCircle),
    APPEARANCE("المظهر والواجهة", Icons.Rounded.Palette),
    ADDONS("الإضافات والملحقات", Icons.Rounded.Extension),
    PLAYBACK("المشغل والتورنت", Icons.Rounded.PlayCircle),
    SERVICES("الخدمات والمفاتيح", Icons.Rounded.VpnKey)
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    profiles: List<UserProfile>,
    addons: List<StremioAddon>,
    cloudStreamRepos: List<CloudStreamRepo>,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSyncStremio: (String, String) -> Unit,
    onInstallAddon: (String) -> Unit,
    onToggleAddon: (StremioAddon) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onInstallCloudStreamRepo: (String) -> Unit,
    onToggleCloudStreamRepo: (CloudStreamRepo) -> Unit,
    onRemoveCloudStreamRepo: (String) -> Unit,
    onSwitchProfile: (Int) -> Unit,
    onAddProfile: (String) -> Unit
) {
    var currentTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    val accentColor = Color(settings.accentColorHex)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 26.dp, start = 28.dp, end = 86.dp, bottom = 24.dp)
    ) {
        // Left Categories Menu (Nuvio Settings Layout)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(NuvioSurface)
                .padding(14.dp)
        ) {
            Text(
                text = "إعدادات Filmr",
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp, start = 6.dp)
            )
            SettingsTab.entries.forEach { tab ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val selected = currentTab == tab
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                focused -> Color.White
                                selected -> accentColor
                                else -> Color.Transparent
                            }
                        )
                        .focusable(interactionSource = interaction)
                        .clickable(interactionSource = interaction, indication = null) { currentTab = tab }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.titleAr,
                        tint = if (focused || selected) Color.Black else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = tab.titleAr,
                        color = if (focused || selected) Color.Black else Color.White,
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.width(22.dp))

        // Right Settings Content Area
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(NuvioSurface)
                .padding(22.dp)
        ) {
            when (currentTab) {
                SettingsTab.ACCOUNT -> {
                    item {
                        SettingsSectionCard(title = "الملفات الشخصية (حتى 5 مستخدمين)", accentColor = accentColor) {
                            var newProfileName by remember { mutableStateOf("") }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(profiles) { prof ->
                                    val interaction = remember { MutableInteractionSource() }
                                    val focused by interaction.collectIsFocusedAsState()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (prof.isActive) accentColor.copy(alpha = 0.25f) else NuvioCard)
                                            .border(
                                                width = if (focused || prof.isActive) 2.dp else 1.dp,
                                                color = if (focused) Color.White else if (prof.isActive) accentColor else Color(0x22FFFFFF),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .focusable(interactionSource = interaction)
                                            .clickable(interactionSource = interaction, indication = null) { onSwitchProfile(prof.id) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(prof.avatarColorHex))
                                        ) {
                                            Text(prof.name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(prof.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            if (profiles.size < 5) {
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newProfileName,
                                        onValueChange = { newProfileName = it },
                                        placeholder = { Text("اسم الملف الجديد...", color = TextSecondary, fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            onAddProfile(newProfileName)
                                            newProfileName = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                                    ) {
                                        Text("إضافة ملف", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = "مزامنة حساب Stremio وسحب جميع الإضافات", accentColor = accentColor) {
                            var email by remember { mutableStateOf(settings.stremioEmail) }
                            var pass by remember { mutableStateOf("") }

                            if (settings.stremioAuthKey.isNotBlank()) {
                                Text(
                                    text = "متصل بحساب: ${settings.stremioEmail} ✓",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("البريد الإلكتروني لحساب Stremio") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text("كلمة المرور") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onSyncStremio(email, pass) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تسجيل الدخول ومزامنة الإضافات الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                SettingsTab.APPEARANCE -> {
                    item {
                        SettingsSectionCard(title = "الألوان والسمات", accentColor = accentColor) {
                            val palette = listOf(
                                "ذهبي Filmr" to 0xFFE5B869L,
                                "أزرق ملكي" to 0xFF4F86F7L,
                                "بنفسجي Nuvio" to 0xFF8B5CF6L,
                                "زمردي" to 0xFF10B981L,
                                "أحمر سينمائي" to 0xFFEF4444L
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                palette.forEach { (label, hex) ->
                                    val selected = settings.accentColorHex == hex
                                    Button(
                                        onClick = { onUpdateSettings { it.copy(accentColorHex = hex) } },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(hex),
                                            contentColor = Color.Black
                                        ),
                                        border = if (selected) androidx.compose.foundation.BorderStroke(2.5.dp, Color.White) else null
                                    ) {
                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = "تخصيص الواجهة والأداء", accentColor = accentColor) {
                            SettingsToggleRow(
                                title = "الترجمة التلقائية للأوصاف غير المعربة إلى العربية",
                                checked = settings.autoTranslateArabic,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(autoTranslateArabic = v) } }
                            )
                            SettingsToggleRow(
                                title = "إظهار شارة الدقائق المتبقية في متابعة المشاهدة",
                                checked = settings.showContinueWatchingBadge,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(showContinueWatchingBadge = v) } }
                            )
                            SettingsToggleRow(
                                title = "إظهار مجلدات المنصات (Netflix, Apple TV+, Disney+)",
                                checked = settings.showPlatformFolders,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(showPlatformFolders = v) } }
                            )
                            SettingsToggleRow(
                                title = "وضع الأداء الخفيف للأجهزة الضعيفة (إيقاف التكبير المتحرك)",
                                checked = settings.lowRamPerformanceMode,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(lowRamPerformanceMode = v) } }
                            )
                        }
                    }
                }

                SettingsTab.ADDONS -> {
                    item {
                        SettingsSectionCard(title = "إضافات Stremio (Manifest URL)", accentColor = accentColor) {
                            var addonUrl by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = addonUrl,
                                    onValueChange = { addonUrl = it },
                                    placeholder = { Text("https://.../manifest.json", color = TextSecondary) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        onInstallAddon(addonUrl)
                                        addonUrl = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                                ) {
                                    Text("تثبيت الإضافة", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            addons.forEach { addon ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NuvioCard)
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(addon.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(addon.manifestUrl, color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = addon.enabled,
                                        onCheckedChange = { onToggleAddon(addon) }
                                    )
                                    IconButton(onClick = { onRemoveAddon(addon.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = "ملحقات ومستودعات CloudStream", accentColor = accentColor) {
                            var repoUrl by remember { mutableStateOf("") }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "أضف رابط مستودع CloudStream (JSON) لتفعيل البحث والتشغيل من ملحقاته داخل التطبيق مباشرة:",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = repoUrl,
                                        onValueChange = { repoUrl = it },
                                        placeholder = { Text("https://raw.githubusercontent.com/.../repo.json", color = TextSecondary) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            onInstallCloudStreamRepo(repoUrl)
                                            repoUrl = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                                    ) {
                                        Text("إضافة مستودع CloudStream", fontWeight = FontWeight.Bold)
                                    }
                                }
                                // QR Code for quick reference/sharing
                                QrCodeImage(content = "https://github.com/recloudstream/extensions", sizeDp = 115)
                            }

                            Spacer(Modifier.height(12.dp))
                            cloudStreamRepos.forEach { repo ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NuvioCard)
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${repo.name} (${repo.plugins.size} ملحق)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(repo.url, color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = repo.enabled,
                                        onCheckedChange = { onToggleCloudStreamRepo(repo) }
                                    )
                                    IconButton(onClick = { onRemoveCloudStreamRepo(repo.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.PLAYBACK -> {
                    item {
                        SettingsSectionCard(title = "محرك التورنت والترجمة", accentColor = accentColor) {
                            var torrUrl by remember { mutableStateOf(settings.torrServerUrl) }
                            OutlinedTextField(
                                value = torrUrl,
                                onValueChange = {
                                    torrUrl = it
                                    onUpdateSettings { s -> s.copy(torrServerUrl = it) }
                                },
                                label = { Text("عنوان محرك TorrServer (الافتراضي: http://127.0.0.1:8090)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            SettingsToggleRow(
                                title = "تخطي مقدمة الأنمي تلقائياً (Anime-Skip)",
                                checked = settings.autoSkipAnimeIntro,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(autoSkipAnimeIntro = v) } }
                            )
                            SettingsToggleRow(
                                title = "الوضع الليلي للصوت (موازنة الانفجارات والأصوات العالية)",
                                checked = settings.nightModeAudio,
                                accentColor = accentColor,
                                onToggle = { v -> onUpdateSettings { it.copy(nightModeAudio = v) } }
                            )
                        }
                    }
                }

                SettingsTab.SERVICES -> {
                    item {
                        SettingsSectionCard(title = "الخدمات المرتبطة ومفاتيح API", accentColor = accentColor) {
                            var mdbKey by remember { mutableStateOf(settings.mdblistApiKey) }
                            var animeSkipKey by remember { mutableStateOf(settings.animeSkipClientId) }

                            OutlinedTextField(
                                value = mdbKey,
                                onValueChange = {
                                    mdbKey = it
                                    onUpdateSettings { s -> s.copy(mdblistApiKey = it) }
                                },
                                label = { Text("مفتاح MDBList API (لتقييمات IMDb / Rotten Tomatoes / Metacritic / MAL)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = animeSkipKey,
                                onValueChange = {
                                    animeSkipKey = it
                                    onUpdateSettings { s -> s.copy(animeSkipClientId = it) }
                                },
                                label = { Text("معرف Anime-Skip Client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101218))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
        )
    }
}
