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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.components.*
import com.filmr.tv.ui.theme.*

@Composable
fun HomeScreen(
    heroItem: MediaItem?,
    continueWatching: List<ContinueWatchingItem>,
    homeRows: Map<String, List<MediaItem>>,
    isLoading: Boolean,
    settings: AppSettings,
    onItemFocused: (MediaItem) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenArtworkPicker: (MediaItem) -> Unit,
    onResumeContinueWatching: (ContinueWatchingItem) -> Unit,
    onRemoveContinueWatching: (String) -> Unit,
    onQuickFilterPlatform: (String) -> Unit
) {
    val accentColor = Color(settings.accentColorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-Bleed Nuvio Hero Backdrop
        if (heroItem != null) {
            AsyncImage(
                model = heroItem.backdropUrl ?: heroItem.posterUrl,
                contentDescription = heroItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
                    .align(Alignment.TopCenter)
            )
            // Cinematic Gradient Vignettes (Bottom + Right Rail fade)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x4407080B),
                            0.42f to Color(0xAA07080B),
                            0.72f to MaterialTheme.colorScheme.background,
                            1.0f to MaterialTheme.colorScheme.background
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xCC07080B),
                            0.45f to Color(0x5507080B),
                            0.85f to Color(0xBB07080B),
                            1.0f to Color(0xEE07080B)
                        )
                    )
            )
        }

        if (isLoading && homeRows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentColor)
                    Spacer(Modifier.height(14.dp))
                    Text("جاري تحميل العروض والكتالوجات...", color = TextPrimary, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 28.dp, bottom = 60.dp, start = 28.dp, end = 86.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Nuvio Hero Info Area (ClearLogo or Title + Metadata + Overview + Quick Actions)
                item(key = "hero_header") {
                    if (heroItem != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .padding(top = 8.dp, bottom = 6.dp)
                        ) {
                            if (!heroItem.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroItem.logoUrl,
                                    contentDescription = heroItem.title,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .height(86.dp)
                                        .widthIn(max = 340.dp)
                                )
                            } else {
                                Text(
                                    text = heroItem.title,
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Metadata Pills: Type • Year • Runtime • IMDb Rating
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetaTagPill(
                                    text = if (heroItem.type == MediaType.SERIES) "مسلسل" else "فيلم",
                                    bg = accentColor,
                                    fg = Color.Black
                                )
                                if (heroItem.year.isNotBlank()) {
                                    MetaTagPill(text = heroItem.year)
                                }
                                if (heroItem.runtime.isNotBlank()) {
                                    MetaTagPill(text = heroItem.runtime)
                                }
                                heroItem.ratings.imdb?.let { imdb ->
                                    MetaTagPill(text = "IMDb ★ $imdb", bg = Color(0x33F5C518), fg = FilmrGoldBright)
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            if (heroItem.overview.isNotBlank()) {
                                Text(
                                    text = heroItem.overview,
                                    color = Color(0xFFE2E6F0),
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 2. Continue Watching Landscape Row
                if (continueWatching.isNotEmpty()) {
                    item(key = "continue_watching_row") {
                        Column {
                            Text(
                                text = "متابعة المشاهدة",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(continueWatching, key = { it.mediaId }) { cw ->
                                    ContinueWatchingLandscapeCard(
                                        item = cw,
                                        showBadge = settings.showContinueWatchingBadge,
                                        accentColor = accentColor,
                                        onClick = { onResumeContinueWatching(cw) },
                                        onRemove = { onRemoveContinueWatching(cw.mediaId) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Platform Folders Row (Netflix, Apple TV+, Disney+, HBO, Anime)
                if (settings.showPlatformFolders) {
                    item(key = "platform_folders_row") {
                        val platforms = listOf(
                            "Netflix" to Color(0xFFE50914),
                            "Apple TV+" to Color(0xFF9CA3AF),
                            "Disney+" to Color(0xFF113CCF),
                            "HBO Max" to Color(0xFF7C3AED),
                            "Prime Video" to Color(0xFF00A8E1),
                            "Anime" to accentColor
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(platforms) { (name, color) ->
                                val interaction = remember { MutableInteractionSource() }
                                val isFocused by interaction.collectIsFocusedAsState()
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isFocused) color else Color(0x22FFFFFF))
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (isFocused) Color.White else color.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .focusable(interactionSource = interaction)
                                        .clickable(interactionSource = interaction, indication = null) {
                                            onQuickFilterPlatform(name)
                                        }
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. All Dynamic Catalog Rows (Trending, Popular Movies, Top TV, Netflix, Apple TV+, Anime)
                homeRows.forEach { (rowTitle, itemsList) ->
                    if (itemsList.isNotEmpty()) {
                        item(key = "row_$rowTitle") {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Text(
                                        text = rowTitle,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "اضغط مطولاً لتغيير البوستر",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(itemsList, key = { "${rowTitle}_${it.id}" }) { media ->
                                        NuvioPosterCard(
                                            item = media,
                                            posterScale = settings.posterSizeScale,
                                            lowRamMode = settings.lowRamPerformanceMode,
                                            onFocused = onItemFocused,
                                            onClick = onOpenDetails,
                                            onLongClick = onOpenArtworkPicker
                                        )
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
private fun MetaTagPill(
    text: String,
    bg: Color = Color(0x28FFFFFF),
    fg: Color = Color.White
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
