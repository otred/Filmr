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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.components.NuvioPosterCard
import com.filmr.tv.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SearchScreen(
    query: String,
    isSearching: Boolean,
    results: List<MediaItem>,
    settings: AppSettings,
    onQueryChange: (String) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenArtworkPicker: (MediaItem) -> Unit
) {
    val accentColor = Color(settings.accentColorHex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 28.dp, start = 32.dp, end = 86.dp, bottom = 24.dp)
    ) {
        Text(
            text = "البحث الشامل",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("ابحث عن فيلم، مسلسل، أو أنمي...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accentColor) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "مسح", tint = TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = accentColor
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.75f)
        )

        Spacer(Modifier.height(20.dp))

        if (isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد نتائج مطابقة لـ "$query"", color = TextSecondary, fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    NuvioPosterCard(
                        item = item,
                        posterScale = settings.posterSizeScale,
                        lowRamMode = settings.lowRamPerformanceMode,
                        onClick = onOpenDetails,
                        onLongClick = onOpenArtworkPicker
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    libraryItems: List<MediaItem>,
    settings: AppSettings,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenArtworkPicker: (MediaItem) -> Unit
) {
    val accentColor = Color(settings.accentColorHex)
    var selectedFilter by remember { mutableStateOf("الكل") }
    val filters = listOf("الكل", "أفلام", "مسلسلات")

    val filtered = remember(libraryItems, selectedFilter) {
        when (selectedFilter) {
            "أفلام" -> libraryItems.filter { it.type == MediaType.MOVIE }
            "مسلسلات" -> libraryItems.filter { it.type == MediaType.SERIES || it.type == MediaType.ANIME }
            else -> libraryItems
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 28.dp, start = 32.dp, end = 86.dp, bottom = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "مكتبتي (${filtered.size})",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                filters.forEach { f ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    val selected = selectedFilter == f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    focused -> Color.White
                                    selected -> accentColor
                                    else -> Color(0x22FFFFFF)
                                }
                            )
                            .focusable(interactionSource = interaction)
                            .clickable(interactionSource = interaction, indication = null) { selectedFilter = f }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = f,
                            color = if (focused || selected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("مكتبتك فارغة حالياً. أضف أفلامك ومسلسلاتك المفضلة لتظهر هنا.", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    NuvioPosterCard(
                        item = item,
                        posterScale = settings.posterSizeScale,
                        lowRamMode = settings.lowRamPerformanceMode,
                        onClick = onOpenDetails,
                        onLongClick = onOpenArtworkPicker
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(
    entries: List<CalendarEpisodeEntry>,
    selectedDay: Int,
    accentColor: Color,
    onSelectDay: (Int) -> Unit,
    onOpenSeriesById: (String) -> Unit
) {
    val today = remember { LocalDate.now() }
    val monthName = remember { today.month.getDisplayName(TextStyle.FULL, Locale("ar")) }
    val daysInMonth = remember { today.lengthOfMonth() }
    val dayEntries = remember(entries, selectedDay) {
        entries.filter { it.dayOfMonth == selectedDay }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 28.dp, start = 32.dp, end = 86.dp, bottom = 24.dp)
    ) {
        Text(
            text = "تقويم الحلقات القادمة • $monthName ${today.year}",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "يعرض مواعيد الحلقات الجديدة للمسلسلات المضافة في مكتبتك",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        // Horizontal Days Strip (1..daysInMonth)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..daysInMonth).toList()) { day ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val selected = selectedDay == day
                val hasRelease = entries.any { it.dayOfMonth == day }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                focused -> Color.White
                                selected -> accentColor
                                else -> NuvioCard
                            }
                        )
                        .border(
                            width = if (hasRelease && !selected) 1.5.dp else 1.dp,
                            color = if (hasRelease) accentColor else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .focusable(interactionSource = interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelectDay(day) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "$day",
                        color = if (focused || selected) Color.Black else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (hasRelease) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (focused || selected) Color.Black else accentColor)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (dayEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد حلقات مجدولة في يوم $selectedDay $monthName", color = TextSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(dayEntries) { ep ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (focused) Color(0x33FFFFFF) else NuvioCard)
                            .border(if (focused) 2.dp else 1.dp, if (focused) accentColor else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                            .focusable(interactionSource = interaction)
                            .clickable(interactionSource = interaction, indication = null) { onOpenSeriesById(ep.mediaId) }
                            .padding(14.dp)
                    ) {
                        AsyncImage(
                            model = ep.posterUrl,
                            contentDescription = ep.seriesTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp, 90.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(ep.seriesTitle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "الموسم ${ep.season} • الحلقة ${ep.episode} — ${ep.episodeTitle}",
                                color = accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonCreditsScreen(
    personName: String,
    works: List<MediaItem>,
    settings: AppSettings,
    onOpenDetails: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 28.dp, start = 32.dp, end = 86.dp, bottom = 24.dp)
    ) {
        Text(
            text = "أعمال: $personName",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(works, key = { it.id }) { item ->
                NuvioPosterCard(
                    item = item,
                    posterScale = settings.posterSizeScale,
                    lowRamMode = settings.lowRamPerformanceMode,
                    onClick = onOpenDetails
                )
            }
        }
    }
}
