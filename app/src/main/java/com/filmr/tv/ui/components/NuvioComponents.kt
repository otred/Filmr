package com.filmr.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.R
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun FloatingRightNavRail(
    currentSection: NavSection,
    expanded: Boolean,
    accentColor: Color,
    activeProfile: UserProfile?,
    onExpandedChange: (Boolean) -> Unit,
    onSelectSection: (NavSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val railWidth by animateDpAsState(if (expanded) 220.dp else 64.dp, label = "railWidth")
    val navItems = listOf(
        Triple(NavSection.HOME, Icons.Rounded.Home, "الرئيسية"),
        Triple(NavSection.SEARCH, Icons.Rounded.Search, "البحث"),
        Triple(NavSection.LIBRARY, Icons.Rounded.VideoLibrary, "المكتبة"),
        Triple(NavSection.CALENDAR, Icons.Rounded.CalendarMonth, "التقويم"),
        Triple(NavSection.SETTINGS, Icons.Rounded.Settings, "الإعدادات")
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xDD0A0B0E),
                        Color(0xF50A0B0E)
                    )
                )
            )
            .onFocusChanged { focusState ->
                onExpandedChange(focusState.hasFocus)
            }
            .padding(vertical = 24.dp, horizontal = 10.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            // Top Brand Badge (Uses exact user logo resource)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (expanded) {
                    Text(
                        text = "FILMR",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.filmr_brand_logo),
                    contentDescription = "Filmr",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                )
            }

            // Center 5 Nuvio Navigation Tabs
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                navItems.forEach { (section, icon, label) ->
                    val interaction = remember { MutableInteractionSource() }
                    val isFocused by interaction.collectIsFocusedAsState()
                    val isSelected = currentSection == section

                    val bgColor = when {
                        isFocused -> accentColor
                        isSelected -> Color(0x28FFFFFF)
                        else -> Color.Transparent
                    }
                    val fgColor = when {
                        isFocused -> Color(0xFF0A0B0E)
                        isSelected -> accentColor
                        else -> TextSecondary
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .focusable(interactionSource = interaction)
                            .clickable(interactionSource = interaction, indication = null) {
                                onSelectSection(section)
                            }
                            .padding(horizontal = 12.dp)
                    ) {
                        if (expanded) {
                            Text(
                                text = label,
                                color = fgColor,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = fgColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Bottom Active User Profile Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (expanded && activeProfile != null) {
                    Text(
                        text = activeProfile.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(activeProfile?.avatarColorHex ?: 0xFFE5B869))
                ) {
                    Text(
                        text = (activeProfile?.name?.firstOrNull() ?: 'F').uppercase(),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    posterScale: Float = 1.0f,
    lowRamMode: Boolean = false,
    onFocused: (MediaItem) -> Unit = {},
    onClick: (MediaItem) -> Unit,
    onLongClick: ((MediaItem) -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused(item)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !lowRamMode) 1.07f else 1.0f,
        label = "posterScale"
    )
    val cardWidth = (142 * posterScale).dp
    val cardHeight = (212 * posterScale).dp

    Column(
        modifier = Modifier
            .width(cardWidth)
            .scale(scale)
            .focusable(interactionSource = interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(item) },
                onLongClick = { onLongClick?.invoke(item) }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(NuvioCard)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.backdropUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // IMDb Rating Pill
            item.ratings.imdb?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC0A0B0E))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = FilmrGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = rating,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Watched Badge
            if (item.isWatched) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD10B981))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "مشاهد",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = if (isFocused) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.year.isNotBlank()) {
            Text(
                text = item.year,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ContinueWatchingLandscapeCard(
    item: ContinueWatchingItem,
    showBadge: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(265.dp)
            .focusable(interactionSource = interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onRemove
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NuvioCard)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0x26FFFFFF),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
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
                            colors = listOf(Color.Transparent, Color(0xDD0A0B0E))
                        )
                    )
            )

            // Remaining minutes badge (Nuvio style)
            if (showBadge && item.remainingMinutes > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC0A0B0E))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "متبقي ${item.remainingMinutes} دقيقة",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom Title + Episode + Progress Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.episodeTitle.isNotBlank()) {
                    Text(
                        text = item.episodeTitle,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    color = accentColor,
                    trackColor = Color(0x44FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun MultiRatingsBar(ratings: RatingsBundle) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ratings.imdb?.let { RatingChip("IMDb", it, Color(0xFFF5C518)) }
        ratings.rottenTomatoes?.let { RatingChip("RT", it, Color(0xFFFA320A)) }
        ratings.metacritic?.let { RatingChip("MC", it, Color(0xFF66CC33)) }
        ratings.trakt?.let { RatingChip("Trakt", it, Color(0xFFED1C24)) }
        ratings.mal?.let { RatingChip("MAL", it, Color(0xFF2E51A2)) }
        ratings.tmdb?.let { RatingChip("TMDB", it, Color(0xFF01B4E4)) }
    }
}

@Composable
private fun RatingChip(source: String, score: String, badgeColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x24FFFFFF))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = source,
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = score,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NuvioCircleActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isFocused -> Color.White
                        isPrimary -> accentColor
                        else -> Color(0x2BFFFFFF)
                    }
                )
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) accentColor else Color(0x33FFFFFF),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused || isPrimary) Color(0xFF0A0B0E) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (isFocused) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun QrCodeImage(content: String, sizeDp: Int = 150) {
    val bitmap = remember(content) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 400, 400)
            val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565)
            for (x in 0 until 400) {
                for (y in 0 until 400) {
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp)
        )
    }
}
