package com.filmr.tv.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.components.*
import com.filmr.tv.ui.theme.*

@Composable
fun DetailsScreen(
    item: MediaItem,
    isLoadingDetails: Boolean,
    selectedSeason: Int,
    seasonEpisodes: List<EpisodeItem>,
    isStreamsDrawerOpen: Boolean,
    isLoadingStreams: Boolean,
    streamsList: List<StreamSource>,
    selectedAddonFilter: String,
    torrentResolvingStatus: String?,
    isArtworkPickerOpen: Boolean,
    artworkOptions: List<TmdbImageOption>,
    isLoadingArtwork: Boolean,
    accentColor: Color,
    onBack: () -> Unit,
    onOpenStreams: (EpisodeItem?) -> Unit,
    onCloseStreams: () -> Unit,
    onFilterAddon: (String) -> Unit,
    onPlayStream: (StreamSource) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleLibrary: (MediaItem) -> Unit,
    onToggleWatched: (MediaItem) -> Unit,
    onOpenArtworkPicker: (MediaItem) -> Unit,
    onCloseArtworkPicker: () -> Unit,
    onApplyArtwork: (MediaItem, TmdbImageOption) -> Unit,
    onToggleTitleLang: (MediaItem) -> Unit,
    onOpenPerson: (CastMember) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full Backdrop
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x8807080B),
                        0.45f to Color(0xDD07080B),
                        1.0f to Color(0xFA07080B)
                    )
                )
        )

        LazyColumn(
            contentPadding = PaddingValues(top = 24.dp, bottom = 60.dp, start = 36.dp, end = 86.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header: ClearLogo + Title + Genres + Ratings + Nuvio Circular Action Buttons
            item(key = "details_top") {
                Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                    if (!item.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .height(90.dp)
                                .widthIn(max = 360.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (item.originalTitle.isNotBlank() && item.originalTitle != item.title) {
                        Text(
                            text = item.originalTitle,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Metadata & Genres
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (item.year.isNotBlank()) Text(item.year, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (item.runtime.isNotBlank()) Text("•  ${item.runtime}", color = TextSecondary, fontSize = 14.sp)
                        if (item.genres.isNotEmpty()) {
                            Text("•  ${item.genres.take(3).joinToString(" ، ")}", color = TextSecondary, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Multi-Rating Row (IMDb, Rotten Tomatoes, Metacritic, Trakt, MAL, TMDB)
                    MultiRatingsBar(ratings = item.ratings)

                    Spacer(Modifier.height(16.dp))

                    // Overview
                    if (item.overview.isNotBlank()) {
                        Text(
                            text = item.overview,
                            color = Color(0xFFE6EAF4),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    // Nuvio Circular Action Row (Play, Library, Watched, Trailer, Change Poster/Title)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NuvioCircleActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            label = "تشغيل",
                            accentColor = accentColor,
                            isPrimary = true,
                            onClick = {
                                val firstEp = seasonEpisodes.firstOrNull()
                                onOpenStreams(firstEp)
                            }
                        )
                        NuvioCircleActionButton(
                            icon = if (item.inLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            label = if (item.inLibrary) "بالمكتبة" else "للمكتبة",
                            accentColor = accentColor,
                            onClick = { onToggleLibrary(item) }
                        )
                        NuvioCircleActionButton(
                            icon = if (item.isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.Visibility,
                            label = if (item.isWatched) "مشاهد" else "تمييز كمشاهد",
                            accentColor = accentColor,
                            onClick = { onToggleWatched(item) }
                        )
                        if (!item.trailerYoutubeKey.isNullOrBlank()) {
                            NuvioCircleActionButton(
                                icon = Icons.Rounded.Movie,
                                label = "الإعلان",
                                accentColor = accentColor,
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/watch?v=${item.trailerYoutubeKey}")
                                    )
                                    runCatching { context.startActivity(intent) }
                                }
                            )
                        }
                        NuvioCircleActionButton(
                            icon = Icons.Rounded.Image,
                            label = "تغيير البوستر",
                            accentColor = accentColor,
                            onClick = { onOpenArtworkPicker(item) }
                        )
                        NuvioCircleActionButton(
                            icon = Icons.Rounded.Translate,
                            label = "عربي / EN",
                            accentColor = accentColor,
                            onClick = { onToggleTitleLang(item) }
                        )
                    }
                }
            }

            // Seasons & Episodes Section (For Series / Anime)
            if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) {
                item(key = "seasons_bar") {
                    Column {
                        Text(
                            text = "المواسم والحلقات",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items((1..10).toList()) { seasonNum ->
                                val interaction = remember { MutableInteractionSource() }
                                val focused by interaction.collectIsFocusedAsState()
                                val selected = selectedSeason == seasonNum
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                focused -> Color.White
                                                selected -> accentColor
                                                else -> Color(0x26FFFFFF)
                                            }
                                        )
                                        .focusable(interactionSource = interaction)
                                        .clickable(interactionSource = interaction, indication = null) {
                                            onSelectSeason(seasonNum)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "الموسم $seasonNum",
                                        color = if (focused || selected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(seasonEpisodes, key = { it.id }) { ep ->
                                EpisodeCard(
                                    episode = ep,
                                    accentColor = accentColor,
                                    onClick = { onOpenStreams(ep) }
                                )
                            }
                        }
                    }
                }
            }

            // Cast & Directors Row (Nuvio bottom section - clicking opens PersonCredits and Back returns cleanly here!)
            if (item.cast.isNotEmpty() || item.directors.isNotEmpty()) {
                item(key = "cast_section") {
                    val allPeople = item.directors + item.cast
                    Column {
                        Text(
                            text = "طاقم العمل والمخرجون",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(allPeople, key = { "${it.roleType}_${it.id}" }) { person ->
                                PersonChipCard(person = person, accentColor = accentColor) {
                                    onOpenPerson(person)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nuvio Left-Side Streams Overlay Drawer (Shows all Stremio Addons + CloudStream Plugins Tabs & Sources)
        if (isStreamsDrawerOpen) {
            StreamsOverlayDrawer(
                item = item,
                isLoading = isLoadingStreams,
                streams = streamsList,
                selectedFilter = selectedAddonFilter,
                torrentResolvingStatus = torrentResolvingStatus,
                accentColor = accentColor,
                onFilterSelected = onFilterAddon,
                onSelectStream = onPlayStream,
                onDismiss = onCloseStreams
            )
        }

        // Custom Poster / Backdrop Picker Overlay
        if (isArtworkPickerOpen) {
            ArtworkPickerOverlay(
                item = item,
                isLoading = isLoadingArtwork,
                options = artworkOptions,
                accentColor = accentColor,
                onSelectOption = { onApplyArtwork(item, it) },
                onDismiss = onCloseArtworkPicker
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NuvioCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x26FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color(0xFF101218))
        ) {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0A0B0E))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "حلقة ${episode.episode}",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = episode.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PersonChipCard(
    person: CastMember,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(105.dp)
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(NuvioCard)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) accentColor else Color(0x33FFFFFF),
                    shape = CircleShape
                )
        ) {
            if (!person.profileUrl.isNullOrBlank()) {
                AsyncImage(
                    model = person.profileUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = person.name,
            color = if (isFocused) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = person.character.ifBlank { person.roleType },
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StreamsOverlayDrawer(
    item: MediaItem,
    isLoading: Boolean,
    streams: List<StreamSource>,
    selectedFilter: String,
    torrentResolvingStatus: String?,
    accentColor: Color,
    onFilterSelected: (String) -> Unit,
    onSelectStream: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    val addonNames = remember(streams) {
        listOf("الكل") + streams.map { it.addonName }.distinct()
    }
    val filteredStreams = remember(streams, selectedFilter) {
        if (selectedFilter == "الكل") streams else streams.filter { it.addonName == selectedFilter }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.54f)
                .align(Alignment.CenterStart)
                .background(Color(0xF5101218))
                .border(1.dp, Color(0x33FFFFFF))
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "مصادر المشاهدة والسيرفرات",
                        color = accentColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = item.title,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Addon Filter Tabs (All, Torrentio, Nuvio HTTP, CloudStream Plugins...)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(addonNames) { name ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    val selected = selectedFilter == name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when {
                                    focused -> Color.White
                                    selected -> accentColor
                                    else -> Color(0x22FFFFFF)
                                }
                            )
                            .focusable(interactionSource = interaction)
                            .clickable(interactionSource = interaction, indication = null) {
                                onFilterSelected(name)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = name,
                            color = if (focused || selected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (torrentResolvingStatus != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(torrentResolvingStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(Modifier.height(10.dp))
                        Text("جاري البحث في جميع الإضافات والسيرفرات...", color = TextPrimary, fontSize = 14.sp)
                    }
                }
            } else if (filteredStreams.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "لا توجد روابط متاحة حالياً. تأكد من تفعيل الإضافات في الإعدادات.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStreams, key = { it.id }) { stream ->
                        StreamSourceRowCard(
                            stream = stream,
                            accentColor = accentColor,
                            onClick = { onSelectStream(stream) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamSourceRowCard(
    stream: StreamSource,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0x38FFFFFF) else Color(0x1CFFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) accentColor else Color(0x24FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        // Quality Badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (stream.quality.contains("4K")) accentColor else Color(0xFF2563EB))
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stream.quality,
                color = if (stream.quality.contains("4K")) Color.Black else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stream.addonName,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                val badgeText = when {
                    stream.isCloudStream -> "CloudStream"
                    stream.isTorrent -> "تورنت فوري"
                    else -> "بث مباشر HTTP"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x28FFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badgeText, color = Color.White, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stream.description.ifBlank { stream.title },
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (stream.sizeText.isNotBlank() || stream.seeds != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        stream.sizeText.takeIf { it.isNotBlank() }?.let { "الحجم: $it" },
                        stream.seeds?.let { "المتصلون: $it" }
                    ).joinToString("  •  "),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ArtworkPickerOverlay(
    item: MediaItem,
    isLoading: Boolean,
    options: List<TmdbImageOption>,
    accentColor: Color,
    onSelectOption: (TmdbImageOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(NuvioSurface)
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "اختر بوستر أو خلفية مخصصة لـ ${item.title}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(options) { opt ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .height(if (opt.isBackdrop) 110.dp else 210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (focused) 3.dp else 1.dp,
                                    color = if (focused) accentColor else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = interaction)
                                .clickable(interactionSource = interaction, indication = null) {
                                    onSelectOption(opt)
                                }
                        ) {
                            AsyncImage(
                                model = opt.url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (opt.isBackdrop) "خلفية (${opt.lang})" else "بوستر (${opt.lang})",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
